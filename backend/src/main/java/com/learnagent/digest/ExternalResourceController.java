package com.learnagent.digest;

import com.learnagent.websearch.WebContentCleaner;
import com.learnagent.websearch.ResourceQualityReviewer;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
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
  private final HiddenResourceSourceRepository hidden;
  public ExternalResourceController(ExternalResourceRepository resources, WorkspaceProjectRepository projects,
                                    WorkspacePathPolicy paths, HiddenResourceSourceRepository hidden) {
    this.hidden = hidden;
    this.resources = resources; this.projects = projects; this.paths = paths;
  }

  @GetMapping
  public List<ResourceView> list(@RequestParam(required = false) UUID courseId) {
    Set<String> blocked = blockedHosts();
    return resources.findAllByOrderByDiscoveredAtDesc().stream().filter(r -> !blocked.contains(ResourceQualityReviewer.host(r.getUrl())))
        .filter(value -> courseId == null || courseId.equals(value.getCourseId()))
        .sorted(qualityOrder())
        .map(this::view)
        .toList();
  }

  @GetMapping("/collections")
  public List<CollectionView> collections() {
    Set<String> blocked = blockedHosts();
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
    return grouped.entrySet().stream().map(entry -> collection(entry.getKey(), entry.getValue(), bySlug, blocked))
        .sorted(Comparator.comparing(CollectionView::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
        .toList();
  }

  private CollectionView collection(String path, List<ExternalResource> values, Map<String, WorkspaceProject> bySlug, Set<String> blocked) {
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
        values.stream().filter(r -> !blocked.contains(ResourceQualityReviewer.host(r.getUrl())))
            .sorted(qualityOrder()).map(this::view).toList(),
        values.stream().anyMatch(r -> blocked.contains(ResourceQualityReviewer.host(r.getUrl()))));
  }

  private Comparator<ExternalResource> qualityOrder() {
    return Comparator.comparing(ExternalResource::getReviewScore, Comparator.nullsLast(Comparator.reverseOrder()))
        .thenComparing(ExternalResource::getUrl).thenComparing(ExternalResource::getId);
  }

  private Set<String> blockedHosts() {
    return new HashSet<>(hidden.findAll().stream().map(HiddenResourceSource::getHost).toList());
  }

  @GetMapping("/hidden-sources")
  public List<String> hiddenSources() { return blockedHosts().stream().sorted().toList(); }

  @PutMapping("/{id}/hide-source")
  public void hideSource(@PathVariable UUID id) {
    ExternalResource resource = resources.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    String host = ResourceQualityReviewer.host(resource.getUrl());
    if (host.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "来源网址无效");
    hidden.save(new HiddenResourceSource(host));
  }

  @DeleteMapping("/hidden-sources/{host}")
  public void restoreSource(@PathVariable String host) { hidden.deleteById(host); }

  private ResourceView view(ExternalResource value) {
    return new ResourceView(value.getId(), value.getCourseId(), value.getDigestId(),
        WebContentCleaner.clean(value.getTitle()), value.getUrl(), value.getSourceName(),
        WebContentCleaner.clean(value.getSummary()), value.getPublishedAt(), value.getDiscoveredAt(),
        value.getLocalMarkdownPath(), value.getReviewScore(), value.getReviewTier(), value.getReviewStatus(), value.getReviewReason());
  }

  public record ResourceView(UUID id, UUID courseId, UUID digestId, String title, String url,
      String sourceName, String summary, Instant publishedAt, Instant discoveredAt, String markdownPath, Integer reviewScore, String reviewTier, String reviewStatus, String reviewReason) { }
  public record CollectionView(String id, String kind, UUID workspaceId, UUID courseId, String title,
      String markdownPath, Instant updatedAt, List<ResourceView> resources, boolean hasHiddenSources) { }
}
