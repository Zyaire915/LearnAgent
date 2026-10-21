package com.learnagent.websearch;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.learnagent.websearch.WebSearchProvider.WebSearchResult;

class ResourceSelectionTest {
  private WebSearchResult item(String url, String summary) {
    return new WebSearchResult("Java", summary, url, "source", null);
  }

  @Test void normalizesTrackingFragmentsDefaultPortsButKeepsContentParameters() {
    assertThat(ResourceSelection.canonicalUrl("https://WWW.example.org:443/a/?b=2&utm_source=x&a=1#intro"))
        .isEqualTo(ResourceSelection.canonicalUrl("http://example.org/a?a=1&b=2"));
    assertThat(ResourceSelection.canonicalUrl("https://example.org/watch?v=one"))
        .isNotEqualTo(ResourceSelection.canonicalUrl("https://example.org/watch?v=two"));
  }

  @Test void removesHistoricNearCopiesWithoutCollapsingDifferentLessonsOrShortSummaries() {
    String summary = "Java collections provide several different implementations of lists and maps with distinct performance tradeoffs.";
    var history = List.of(item("https://example.org/old", summary));
    var selected = ResourceSelection.select("Java", List.of(
        item("https://example.org/old?utm_medium=email", ""),
        item("https://copy.example/java", summary + " More."),
        item("https://example.org/new", "Java memory management uses garbage collection and object allocation to manage heap space safely."),
        item("https://example.org/short1", "Java tutorial"), item("https://another.example/short2", "Java tutorial")),
        history, 5, Instant.now());
    assertThat(selected).extracting(WebSearchResult::url)
        .containsExactlyInAnyOrder("https://example.org/new", "https://example.org/short1", "https://another.example/short2");
  }

  @Test void explicitGlobalInstitutionsAndEducationPlatformsDoNotTrustSpoofedOrArbitraryEduHosts() {
    var reviewer = new ResourceQualityReviewer();
    for (String host : List.of("tsinghua.edu.cn", "ethz.ch", "nus.edu.sg", "utoronto.ca", "iitm.ac.in", "uct.ac.za")) {
      assertThat(reviewer.rank("Java", List.of(item("https://" + host + "/course", "")), 1, Instant.now())
          .getFirst().review().tier()).isEqualTo("ACADEMIC");
    }
    for (String host : List.of("unknown.edu", "mit.edu.evil.example")) {
      assertThat(reviewer.rank("Java", List.of(item("https://" + host + "/course", "")), 1, Instant.now())
          .getFirst().review().tier()).isEqualTo("OTHER");
    }
    assertThat(reviewer.rank("Java", List.of(item("https://www.coursera.org/learn/java", "")), 1, Instant.now())
        .getFirst().review().tier()).isEqualTo("EDUCATION");
  }
}
