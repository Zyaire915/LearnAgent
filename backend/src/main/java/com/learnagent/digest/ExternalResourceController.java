package com.learnagent.digest;

import com.learnagent.websearch.WebContentCleaner;
import com.learnagent.workspace.*;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/external-resources")
public class ExternalResourceController {
  private final ExternalResourceRepository resources;
  private final WorkspaceProjectRepository projects;
  private final WorkspacePathPolicy paths;
  public ExternalResourceController(ExternalResourceRepository resources, WorkspaceProjectRepository projects,
                                    WorkspacePathPolicy paths) {
    this.resources = resources; this.projects = projects; this.paths = paths;
  }

  @GetMapping
  public List<ResourceView> list(@RequestParam(required = false) UUID courseId) {
    return resources.findAllByOrderByDiscoveredAtDesc().stream()
        .filter(value -> courseId == null || courseId.equals(value.getCourseId()))
        .map(this::view)
        .toList();
  }

  @GetMapping("/collections")
  public List<CollectionView> collections() {
    List<ExternalResource> values = resources.findAllByOrderByDiscoveredAtDesc();
    Map<String, List<ExternalResource>> grouped = new LinkedHashMap<>();
    values.stream().filter(value -> value.getLocalMarkdownPath() != null && !value.getLocalMarkdownPath().isBlank())
        .forEach(value -> grouped.computeIfAbsent(value.getLocalMarkdownPath(), ignored -> new ArrayList<>()).add(value));
    Map<String, WorkspaceProject> bySlug = new LinkedHashMap<>();
    for (WorkspaceProject project : projects.findAllByOrderByUpdatedAtDesc()) {
      bySlug.put(project.getSlug(), project);
      String researchPath = project.getSlug() + "/resources/research.md";
      if (Files.isRegularFile(paths.resolveFile(researchPath))) grouped.putIfAbsent(researchPath, new ArrayList<>());
    }
    return grouped.entrySet().stream().map(entry -> collection(entry.getKey(), entry.getValue(), bySlug))
        .sorted(Comparator.comparing(CollectionView::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
        .toList();
  }

  private CollectionView collection(String path, List<ExternalResource> values, Map<String, WorkspaceProject> bySlug) {
    boolean daily = path.startsWith("_inbox/");
    String slug = daily ? null : path.split("/", 2)[0];
    WorkspaceProject project = slug == null ? null : bySlug.get(slug);
    String title = daily ? "每日资料 · " + path.replace("_inbox/", "").replace("/daily-digest.md", "")
        : project == null ? slug : project.getName();
    UUID courseId = project == null ? values.stream().map(ExternalResource::getCourseId).filter(Objects::nonNull).findFirst().orElse(null)
        : project.getCourseId();
    Instant updated = values.stream().map(ExternalResource::getDiscoveredAt).filter(Objects::nonNull).max(Comparator.naturalOrder())
        .orElse(project == null ? null : project.getUpdatedAt());
    return new CollectionView(path, daily ? "DAILY_DIGEST" : "PROJECT_RESEARCH",
        project == null ? null : project.getId(), courseId, title, path, updated,
        values.stream().map(this::view).toList());
  }

  private ResourceView view(ExternalResource value) {
    return new ResourceView(value.getId(), value.getCourseId(), value.getDigestId(),
        WebContentCleaner.clean(value.getTitle()), value.getUrl(), value.getSourceName(),
        WebContentCleaner.clean(value.getSummary()), value.getPublishedAt(), value.getDiscoveredAt(),
        value.getLocalMarkdownPath());
  }

  public record ResourceView(UUID id, UUID courseId, UUID digestId, String title, String url,
      String sourceName, String summary, Instant publishedAt, Instant discoveredAt, String markdownPath) { }
  public record CollectionView(String id, String kind, UUID workspaceId, UUID courseId, String title,
      String markdownPath, Instant updatedAt, List<ResourceView> resources) { }
}
