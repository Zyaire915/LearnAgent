package com.learnagent.digest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.learnagent.websearch.ResourceReview;
import com.learnagent.workspace.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

class ExternalResourceControllerTest {
  private final ExternalResourceRepository resources = mock(ExternalResourceRepository.class);
  private final HiddenResourceSourceRepository hidden = mock(HiddenResourceSourceRepository.class);
  private final WorkspaceProjectRepository projects = mock(WorkspaceProjectRepository.class);
  private final ExternalResourceController controller = new ExternalResourceController(resources, projects, mock(WorkspacePathPolicy.class), hidden);

  @Test void hidesWholeNormalizedHostAndFiltersListsAndCollectionsAndCanRestore() {
    var resource = new ExternalResource(null, null, "Java", "https://WWW.example.org/java", "example", "Summary", null, "_inbox/2026-10-19/daily-digest.md");
    when(resources.findById(resource.getId())).thenReturn(Optional.of(resource));
    controller.hideSource(resource.getId());
    var capture = ArgumentCaptor.forClass(HiddenResourceSource.class);
    verify(hidden).save(capture.capture());
    assertThat(capture.getValue().getHost()).isEqualTo("example.org");
    when(hidden.findAll()).thenReturn(List.of(capture.getValue()));
    when(resources.findAllByOrderByDiscoveredAtDesc()).thenReturn(List.of(resource));
    assertThat(controller.list(null)).isEmpty();
    assertThat(controller.collections()).singleElement().satisfies(c -> {
      assertThat(c.resources()).isEmpty();
      assertThat(c.hasHiddenSources()).isTrue();
    });
    controller.restoreSource("example.org");
    verify(hidden).deleteById("example.org");
  }

  @Test void exposesSavedReviewAndKeepsLegacyRecordsExplicitlyUnreviewed() {
    var legacy = new ExternalResource(null, null, "Legacy", "https://example.org/old", "example", "Summary", null, null);
    var reviewed = new ExternalResource(null, null, "Java", "https://dev.java", "dev.java", "Summary", null, null)
        .reviewed(new ResourceReview(67, "OFFICIAL", "AUTO_REVIEWED", "Official and relevant"));
    when(resources.findAllByOrderByDiscoveredAtDesc()).thenReturn(List.of(legacy, reviewed));
    var views = controller.list(null);
    assertThat(views.get(1).reviewStatus()).isEqualTo("UNREVIEWED");
    assertThat(views.get(1).reviewScore()).isNull();
    assertThat(views.getFirst().reviewScore()).isEqualTo(67);
    assertThat(views.getFirst().reviewReason()).isEqualTo("Official and relevant");
    assertThat(views.getFirst().discoveredAt()).isNotNull();
  }

  @Test void unknownResourceReturnsNotFound() {
    assertThatThrownBy(() -> controller.hideSource(UUID.randomUUID())).isInstanceOf(ResponseStatusException.class);
  }
}
