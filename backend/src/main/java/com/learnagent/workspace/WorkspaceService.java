package com.learnagent.workspace;

import com.learnagent.course.CourseService;
import com.learnagent.digest.ExternalResource;
import com.learnagent.digest.ExternalResourceRepository;
import com.learnagent.job.AgentJobService;
import com.learnagent.notification.NotificationService;
import com.learnagent.notification.NotificationType;
import com.learnagent.task.*;
import com.learnagent.websearch.WebSearchProvider;
import com.learnagent.websearch.WebContentCleaner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkspaceService {
  private final WorkspaceProjectRepository projects;
  private final WorkspaceArtifactRepository artifacts;
  private final WorkspacePathPolicy paths;
  private final WorkspaceFileService files;
  private final CourseService courses;
  private final TaskService tasks;
  private final WebSearchProvider web;
  private final ExternalResourceRepository resources;
  private final AgentJobService jobs;
  private final NotificationService notifications;
  private final ObjectProvider<ChatClient.Builder> chat;

  public WorkspaceService(
      WorkspaceProjectRepository projects, WorkspaceArtifactRepository artifacts,
      WorkspacePathPolicy paths, WorkspaceFileService files, CourseService courses,
      TaskService tasks, WebSearchProvider web, ExternalResourceRepository resources,
      AgentJobService jobs, NotificationService notifications, ObjectProvider<ChatClient.Builder> chat) {
    this.projects = projects; this.artifacts = artifacts; this.paths = paths; this.files = files;
    this.courses = courses; this.tasks = tasks; this.web = web; this.resources = resources;
    this.jobs = jobs; this.notifications = notifications; this.chat = chat;
  }

  @Transactional
  public ProjectView create(CreateRequest request) {
    if (request.courseId() != null) courses.get(request.courseId());
    String description = required(request.description(), "请描述你想完成的项目。");
    String name = request.name() == null || request.name().isBlank() ? automaticName(description) : request.name().trim();
    String slug = uniqueSlug(name);
    Path root = paths.resolveDirectory(slug);
    try { Files.createDirectories(root); }
    catch (IOException exception) { throw new IllegalStateException("Could not create workspace directory.", exception); }
    WorkspaceProject project = projects.save(new WorkspaceProject(
        request.courseId(), name, slug,
        request.template() == null ? WorkspaceTemplate.GENERIC : request.template(),
        root.toString(), description));
    return view(project);
  }

  @Transactional(readOnly = true) public List<ProjectView> list() {
    return projects.findAllByOrderByUpdatedAtDesc().stream().map(this::view).toList();
  }
  @Transactional(readOnly = true) public ProjectView get(UUID id) { return view(requiredProject(id)); }

  @Transactional(readOnly = true)
  public OpenFolderResult openFolder(UUID id) {
    WorkspaceProject project = requiredProject(id);
    Path root = paths.resolveDirectory(project.getSlug());
    if (!Files.isDirectory(root)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "项目文件夹不存在。");
    }
    if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) {
      throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "当前运行环境不支持打开系统文件夹。");
    }
    try {
      Path workspaceRoot = paths.root().toRealPath();
      Path verifiedRoot = root.toRealPath();
      if (!verifiedRoot.startsWith(workspaceRoot)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "项目文件夹超出安全工作区。");
      }
      Path explorer = Path.of(System.getenv().getOrDefault("SystemRoot", "C:\\Windows"), "explorer.exe");
      if (!Files.isRegularFile(explorer)) throw new IOException("Windows Explorer is unavailable.");
      new ProcessBuilder(explorer.toString(), verifiedRoot.toString()).start();
      return new OpenFolderResult(verifiedRoot.toString(), true);
    } catch (IOException exception) {
      throw new IllegalStateException("无法打开项目文件夹。", exception);
    }
  }

  @Transactional
  public ProjectView generate(UUID id) {
    WorkspaceProject project = requiredProject(id);
    return jobs.run("WORKSPACE_GENERATE", "workspace-generate-" + id, Map.of("workspaceId", id), context -> {
      project.status(WorkspaceStatus.GENERATING);
      context.progress(10, "正在根据项目描述制定计划");
      ProjectBlueprint blueprint = blueprint(project);
      writeArtifact(project, "PLAN", "PROJECT_PLAN.md", blueprint.planMarkdown(), null);
      context.progress(30, "正在创建关联学习任务");
      createTasks(project, blueprint.tasks());
      context.progress(45, "正在收集项目学习资料");
      generateResearch(project, blueprint.searchQuery());
      context.progress(60, "正在生成项目初始文件");
      if (project.getTemplate() == WorkspaceTemplate.GENERIC) {
        writeArtifact(project, "README", "README.md",
            WorkspaceTemplates.genericReadme(project.getName(), project.getDescription()), null);
        blueprint.files().stream().limit(8).filter(this::safeGeneratedFile).forEach(file ->
            writeArtifact(project, "SOURCE", file.path(), file.content(), null));
      } else if (project.getTemplate() == WorkspaceTemplate.SPRING_BOOT_COURSE) {
        WorkspaceTemplates.springProject(project.getName()).forEach((relative, content) ->
            writeArtifact(project, relative.endsWith(".java") ? "SOURCE" : "CONFIG", relative, content, null));
      } else {
        writeArtifact(project, "README", "README.md",
            WorkspaceTemplates.genericReadme(project.getName(), project.getDescription()), null);
      }
      project.status(WorkspaceStatus.READY);
      notifications.create(NotificationType.WORKSPACE, "学习项目已生成",
          project.getName() + " 已生成项目计划、任务、资料与初始文件。",
          "/workbench?workspaceId=" + id, "WORKSPACE", id, "workspace-ready-" + id);
      context.progress(95, "项目内容已生成");
      return view(project);
    });
  }

  @Transactional
  public BuildResult build(UUID id, String requestedGoal) {
    WorkspaceProject project = requiredProject(id);
    String goal = "package".equalsIgnoreCase(requestedGoal) ? "package" : "test";
    return jobs.run("WORKSPACE_BUILD", null, Map.of("workspaceId", id, "goal", goal), context -> {
      project.status(WorkspaceStatus.BUILDING);
      Path root = paths.resolveDirectory(project.getSlug());
      if (!Files.isRegularFile(root.resolve("pom.xml"))) {
        project.status(WorkspaceStatus.READY);
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
            "当前项目没有 Maven 配置，不能运行该测试；你仍可编辑和查看项目文件。");
      }
      context.progress(10, "正在运行项目测试");
      Path output = paths.resolveFile(project.getSlug() + "/BUILD_OUTPUT.txt");
      List<String> command = List.of(
          "cmd.exe", "/d", "/c", "D:\\LearnAgent\\mvnw.cmd",
          "-f", root.resolve("pom.xml").toString(),
          "-Dmaven.repo.local=D:\\LearnAgent\\.mvn-cache", goal);
      ProcessBuilder builder = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true);
      builder.redirectOutput(output.toFile());
      builder.environment().put("JAVA_TOOL_OPTIONS", "-Djava.io.tmpdir=D:\\LearnAgent\\.tmp -Xss512k");
      try {
        Process process = builder.start();
        boolean finished = process.waitFor(180, TimeUnit.SECONDS);
        if (!finished) {
          process.destroyForcibly(); project.status(WorkspaceStatus.BUILD_FAILED);
          throw new IllegalStateException("Maven build timed out after 180 seconds.");
        }
        String log = Files.readString(output, StandardCharsets.UTF_8);
        recordArtifact(project, "BUILD_LOG", "BUILD_OUTPUT.txt", null);
        boolean success = process.exitValue() == 0;
        project.status(success ? WorkspaceStatus.BUILD_SUCCESS : WorkspaceStatus.BUILD_FAILED);
        context.progress(95, success ? "Maven build succeeded" : "Maven build failed");
        return new BuildResult(success, process.exitValue(), goal, log);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt(); project.status(WorkspaceStatus.BUILD_FAILED);
        throw new IllegalStateException("Maven build was interrupted.", exception);
      } catch (IOException exception) {
        project.status(WorkspaceStatus.BUILD_FAILED);
        throw new IllegalStateException("Could not run allowlisted Maven build.", exception);
      }
    });
  }

  @Transactional(readOnly = true)
  public List<TreeEntry> tree(UUID id) {
    WorkspaceProject project = requiredProject(id);
    Path root = paths.resolveDirectory(project.getSlug());
    try (var stream = Files.walk(root, 8)) {
      return stream.filter(path -> !path.equals(root))
          .filter(path -> !root.relativize(path).toString().startsWith("target"))
          .limit(500)
          .map(path -> new TreeEntry(root.relativize(path).toString().replace('\\', '/'), Files.isDirectory(path), size(path)))
          .sorted(Comparator.comparing(TreeEntry::directory).reversed().thenComparing(TreeEntry::path)).toList();
    } catch (IOException exception) { throw new IllegalStateException("Could not list workspace tree.", exception); }
  }

  @Transactional(readOnly = true)
  public FileView read(UUID id, String relative) {
    WorkspaceProject project = requiredProject(id);
    return new FileView(relative, files.read(project.getSlug() + "/" + relative));
  }
  @Transactional
  public FileView write(UUID id, String relative, String content) {
    WorkspaceProject project = requiredProject(id);
    files.write(project.getSlug() + "/" + relative, content);
    recordArtifact(project, "FILE", relative, null);
    return new FileView(relative, content);
  }
  @Transactional(readOnly = true)
  public List<WorkspaceArtifact> artifactList(UUID id) {
    requiredProject(id); return artifacts.findByWorkspaceIdOrderByCreatedAt(id);
  }

  private void createTasks(WorkspaceProject project, List<BlueprintTask> blueprintTasks) {
    String prefix = "[" + project.getName() + "] ";
    List<BlueprintTask> items = blueprintTasks == null || blueprintTasks.isEmpty()
        ? fallbackTasks(project) : blueprintTasks.stream().filter(this::validTask).limit(6).toList();
    for (int index = 0; index < items.size(); index++) {
      BlueprintTask item = items.get(index);
      tasks.create(new TaskDtos.Request(project.getCourseId(), prefix + item.title(), item.description(),
          Instant.now().plus(index + 1L, ChronoUnit.DAYS), TaskStatus.TODO,
          index == 2 ? TaskPriority.HIGH : TaskPriority.MEDIUM));
    }
  }

  private void generateResearch(WorkspaceProject project, String requestedQuery) {
    List<WebSearchProvider.WebSearchResult> found = List.of();
    if (web.isConfigured()) {
      String query = requestedQuery == null || requestedQuery.isBlank()
          ? project.getDescription() + " 学习资料 官方教程 最佳实践" : requestedQuery;
      try { found = web.search(query, 3); }
      catch (RuntimeException ignored) { found = List.of(); }
    }
    StringBuilder markdown = new StringBuilder("# 外部学习资料\n\n> 以下内容是摘要与来源链接，不替代原始资料。\n\n");
    for (int index = 0; index < found.size(); index++) {
      var item = found.get(index);
      String title = WebContentCleaner.clean(item.title());
      String summary = WebContentCleaner.clean(item.snippet());
      markdown.append("## ").append(index + 1).append(". ").append(title).append("\n\n")
          .append(summary).append("\n\n- 来源：[").append(item.sourceName()).append("](")
          .append(item.url()).append(")\n- 检索时间：").append(Instant.now()).append("\n\n");
      String resourcePath = project.getSlug() + "/resources/research.md";
      if (!resources.existsByUrlAndLocalMarkdownPath(item.url(), resourcePath)) {
        resources.save(new ExternalResource(null, project.getCourseId(), title, item.url(),
            item.sourceName(), summary, item.publishedAt(), resourcePath));
      }
    }
    if (found.isEmpty()) markdown.append("当前未获取到外部资料，可以先根据 PROJECT_PLAN.md 推进，并稍后重新检索。\n");
    writeArtifact(project, "RESOURCE", "resources/research.md", markdown.toString(),
        found.isEmpty() ? null : found.getFirst().url());
  }

  private ProjectBlueprint blueprint(WorkspaceProject project) {
    ProjectBlueprint fallback = new ProjectBlueprint(
        WorkspaceTemplates.genericProjectPlan(project.getName(), project.getDescription()),
        fallbackTasks(project), project.getDescription() + " 学习资料 官方教程 最佳实践", List.of());
    if (project.getTemplate() != WorkspaceTemplate.GENERIC) return fallback;
    ChatClient.Builder builder = availableBuilder();
    if (builder == null) return fallback;
    String courseContext = "未关联课程";
    if (project.getCourseId() != null) {
      var course = courses.get(project.getCourseId());
      courseContext = course.getName() + "：" + Optional.ofNullable(course.getDescription()).orElse("");
    }
    try {
      ProjectBlueprint generated = builder.build().prompt()
          .system("""
              你是学习项目规划助手。根据用户的真实项目描述生成可执行的中文项目蓝图。
              planMarkdown 必须包括目标、成果、阶段、验收标准和后续计划；tasks 为 4 至 6 个具体任务；
              searchQuery 用于检索相关学习资料；files 最多 8 个，只能使用相对路径和常见文本源码扩展名，
              内容应形成一个最小但有用的项目起点。不得把所有项目强行改成 Java 或课程管理系统。
              """)
          .user("项目名称：" + project.getName() + "\n关联课程：" + courseContext
              + "\n用户描述：" + project.getDescription())
          .call().entity(ProjectBlueprint.class);
      if (generated == null) return fallback;
      String plan = generated.planMarkdown() == null || generated.planMarkdown().isBlank()
          ? fallback.planMarkdown() : generated.planMarkdown();
      List<BlueprintTask> taskList = generated.tasks() == null ? fallback.tasks()
          : generated.tasks().stream().filter(this::validTask).limit(6).toList();
      if (taskList.isEmpty()) taskList = fallback.tasks();
      String query = generated.searchQuery() == null || generated.searchQuery().isBlank()
          ? fallback.searchQuery() : generated.searchQuery();
      List<BlueprintFile> initialFiles = generated.files() == null ? List.of()
          : generated.files().stream().filter(this::safeGeneratedFile).limit(8).toList();
      return new ProjectBlueprint(plan, taskList, query, initialFiles);
    } catch (RuntimeException ignored) {
      return fallback;
    }
  }

  private List<BlueprintTask> fallbackTasks(WorkspaceProject project) {
    return List.of(
        new BlueprintTask("明确目标与验收标准", "阅读 PROJECT_PLAN.md，确认项目范围、成果形式和演示重点。"),
        new BlueprintTask("完成主题资料调研", "阅读 resources/research.md，记录与项目描述直接相关的关键知识。"),
        new BlueprintTask("设计项目结构", "把“" + project.getDescription() + "”拆成模块、输入、输出和里程碑。"),
        new BlueprintTask("完成核心成果", "制作一个能够回应项目目标的最小可展示版本。"),
        new BlueprintTask("验证并整理展示", "按验收标准检查成果，记录问题、结论和下一步计划。"));
  }

  private boolean validTask(BlueprintTask task) {
    return task != null && task.title() != null && !task.title().isBlank()
        && task.description() != null && !task.description().isBlank();
  }

  private boolean safeGeneratedFile(BlueprintFile file) {
    if (file == null || file.path() == null || file.path().isBlank() || file.content() == null
        || file.content().length() > 200_000) return false;
    String value = file.path().replace('\\', '/').trim().toLowerCase(Locale.ROOT);
    if (value.startsWith("/") || value.contains("../") || value.matches("^[a-z]:.*")) return false;
    return value.matches(".*\\.(md|txt|java|xml|ya?ml|json|properties|py|js|ts|vue|html|css|sql|csv)$");
  }

  private ChatClient.Builder availableBuilder() {
    try { return chat.getIfAvailable(); }
    catch (BeansException ignored) { return null; }
  }

  private void writeArtifact(WorkspaceProject project, String type, String relative, String content, String source) {
    files.write(project.getSlug() + "/" + relative, content);
    recordArtifact(project, type, relative, source);
  }
  private void recordArtifact(WorkspaceProject project, String type, String relative, String source) {
    if (artifacts.findByWorkspaceIdAndRelativePath(project.getId(), relative).isEmpty()) {
      artifacts.save(new WorkspaceArtifact(project.getId(), type, relative, source,
          checksum(paths.resolveFile(project.getSlug() + "/" + relative))));
    }
  }
  private String checksum(Path path) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
    catch (Exception ignored) { return null; }
  }
  private long size(Path path) { try { return Files.isRegularFile(path) ? Files.size(path) : 0; } catch (IOException ignored) { return 0; } }
  private String uniqueSlug(String name) {
    String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    if (base.isBlank()) base = "learning-project";
    String candidate = base; int number = 2;
    while (projects.findBySlug(candidate).isPresent()) candidate = base + "-" + number++;
    return candidate;
  }
  private String required(String value, String message) {
    if (value == null || value.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    return value.trim();
  }
  private String automaticName(String description) {
    String compact = description.replaceAll("\\s+", " ").trim();
    String first = compact.split("[。！？!?\\n]", 2)[0].trim();
    if (first.length() > 28) first = first.substring(0, 28);
    return first.isBlank() ? "我的学习项目" : first;
  }
  private WorkspaceProject requiredProject(UUID id) {
    return projects.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found."));
  }
  private ProjectView view(WorkspaceProject value) {
    boolean buildSupported = Files.isRegularFile(paths.resolveDirectory(value.getSlug()).resolve("pom.xml"));
    return new ProjectView(value.getId(), value.getCourseId(), value.getName(), value.getSlug(), value.getTemplate(),
        value.getStatus(), value.getRootPath(), value.getDescription(), buildSupported,
        value.getCreatedAt(), value.getUpdatedAt());
  }

  public record CreateRequest(UUID courseId, String name, String description, WorkspaceTemplate template) { }
  public record ProjectView(UUID id, UUID courseId, String name, String slug, WorkspaceTemplate template,
                            WorkspaceStatus status, String rootPath, String description, boolean buildSupported,
                            Instant createdAt, Instant updatedAt) { }
  public record TreeEntry(String path, boolean directory, long size) { }
  public record FileView(String path, String content) { }
  public record FileWrite(String content) { }
  public record BuildResult(boolean success, int exitCode, String goal, String log) { }
  public record OpenFolderResult(String path, boolean opened) { }
  public record BlueprintTask(String title, String description) { }
  public record BlueprintFile(String path, String content) { }
  public record ProjectBlueprint(String planMarkdown, List<BlueprintTask> tasks,
                                 String searchQuery, List<BlueprintFile> files) { }
}
