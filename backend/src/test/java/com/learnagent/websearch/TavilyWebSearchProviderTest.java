package com.learnagent.websearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import com.learnagent.digest.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TavilyWebSearchProviderTest {
  @Test void doesNotRecommendSearchCandidatesWhenBodyReviewReturnsNothing() {
    var builder = RestClient.builder();
    var server = MockRestServiceServer.bindTo(builder).build();
    server.expect(requestTo("https://search.example/search")).andRespond(withSuccess(
        "{\"results\":[{\"title\":\"Java\",\"url\":\"https://dev.java/learn\"}]}", MediaType.APPLICATION_JSON));
    emptyPasses(server, 3);
    var articleReviewer = mock(ArticleContentReviewer.class);
    var result = new TavilyWebSearchProvider("test-key", "https://search.example/search", builder,
        mock(HiddenResourceSourceRepository.class), articleReviewer).search("Java", 5);
    assertThat(result).isEmpty();
    verify(articleReviewer).review(eq("Java"), argThat(items -> items.size() == 1));
    server.verify();
  }
  @Test void preservesMissingVersusMalformedDatesWithoutInventingOtherEvidence() {
    var builder = RestClient.builder();
    var server = MockRestServiceServer.bindTo(builder).build();
    var hidden = mock(HiddenResourceSourceRepository.class);
    server.expect(requestTo("https://search.example/search")).andRespond(withSuccess("""
        {"results":[
          {"title":"Java", "url":"https://missing.example/missing"},
          {"title":"Java", "url":"https://blank.example/blank", "published_date":"  "},
          {"title":"Java", "url":"https://invalid.example/invalid", "published_date":"yesterday-ish"},
          {"title":"Java", "url":"https://date.example/date", "published_date":" 2026-01-01 "},
          {"title":"Java", "url":"https://rfc.example/rfc", "published_date":"Thu, 1 Jan 2026 00:00:00 GMT"},
          {"title":"Java", "url":"https://iso.example/iso", "published_date":"2026-01-01T00:00:00Z"}
        ]}
        """, MediaType.APPLICATION_JSON));
    emptyPasses(server, 3);
    var results = new TavilyWebSearchProvider("test-key", "https://search.example/search", builder, hidden, passthrough()).search("Java", 10);
    assertThat(results).hasSize(6).allSatisfy(r -> {
      assertThat(r.signals().author()).isNull();
      assertThat(r.signals().accessible()).isNull();
      assertThat(r.signals().interactions()).isNull();
      if (r.url().endsWith("invalid")) {
        assertThat(r.review().reason()).contains("发布日期格式异常");
      } else if (r.url().endsWith("missing") || r.url().endsWith("blank")) {
        assertThat(r.review().reason()).contains("发布日期未知");
      } else {
        assertThat(r.publishedAt()).isEqualTo("2026-01-01T00:00:00Z");
        assertThat(r.signals().invalidPublishedDate()).isFalse();
      }
    });
    server.verify();
  }

  @Test void ranksBeforeLimitingFiltersHiddenSourcesAndPreservesMissingMetadata() {
    var builder = RestClient.builder();
    var server = MockRestServiceServer.bindTo(builder).build();
    var hidden = mock(HiddenResourceSourceRepository.class);
    when(hidden.findAll()).thenReturn(List.of(new HiddenResourceSource("dev.java")));
    server.expect(requestTo("https://search.example/search"))
        .andExpect(jsonPath("$.max_results").value(10))
        .andRespond(withSuccess("""
            {"results":[
              {"title":"Java", "url":"https://unknown.example/java", "content":"Tutorial"},
              {"title":"Java", "url":"https://docs.oracle.com/java", "content":"Documentation", "published_date":"2026-01-01"},
              {"title":"Java", "url":"https://dev.java", "content":"Hidden"}
            ]}
            """, MediaType.APPLICATION_JSON));
    emptyPasses(server, 2);
    var result = new TavilyWebSearchProvider("test-key", "https://search.example/search", builder, hidden, passthrough()).search("Java", 1);
    assertThat(result).singleElement().satisfies(r -> {
      assertThat(r.url()).isEqualTo("https://docs.oracle.com/java");
      assertThat(r.publishedAt()).isEqualTo("2026-01-01T00:00:00Z");
      assertThat(r.signals().interactions()).isNull();
      assertThat(r.review().reason()).doesNotContain("互动数据", "可访问性");
    });
    server.verify();
  }

  private ArticleContentReviewer passthrough() {
    var reviewer = mock(ArticleContentReviewer.class);
    when(reviewer.review(anyString(), anyList())).thenAnswer(call -> call.getArgument(1));
    return reviewer;
  }

  private void emptyPasses(MockRestServiceServer server, int count) {
    for (int i = 0; i < count; i++) server.expect(requestTo("https://search.example/search"))
        .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));
  }

  @Test void mergesTargetedSourcesAndRefillsAfterHistoryFilteringToFive() {
    var builder = RestClient.builder();
    var server = MockRestServiceServer.bindTo(builder).build();
    var hidden = mock(HiddenResourceSourceRepository.class);
    server.expect(requestTo("https://search.example/search"))
        .andExpect(jsonPath("$.include_domains").isEmpty())
        .andRespond(withSuccess("""
            {"results":[{"title":"Java","url":"https://example.org/old?utm_source=search"},
            {"title":"Java","url":"https://example.org/new"}]}
            """, MediaType.APPLICATION_JSON));
    server.expect(requestTo("https://search.example/search"))
        .andExpect(jsonPath("$.include_domains").isNotEmpty())
        .andRespond(withSuccess("""
            {"results":[{"title":"Java","url":"https://dev.java/learn"},
            {"title":"Cooking","url":"https://docs.oracle.com/unrelated"}]}
            """, MediaType.APPLICATION_JSON));
    server.expect(requestTo("https://search.example/search"))
        .andExpect(jsonPath("$.query").value("Java course 公开课程"))
        .andRespond(withSuccess("""
            {"results":[{"title":"Java","url":"https://ocw.mit.edu/course"}]}
            """, MediaType.APPLICATION_JSON));
    server.expect(requestTo("https://search.example/search"))
        .andRespond(withSuccess("""
            {"results":[{"title":"Java","url":"https://example.org/four"},
            {"title":"Java","url":"https://fifth.example/five"},
            {"title":"Java","url":"https://example.org/six"}]}
            """, MediaType.APPLICATION_JSON));
    var history = List.of(new WebSearchProvider.WebSearchResult("Java", "", "http://www.example.org/old#intro", "example", null));
    var results = new TavilyWebSearchProvider("test-key", "https://search.example/search", builder, hidden, passthrough()).search("Java", 5, history);
    assertThat(results).hasSize(5);
    assertThat(results.subList(0, 2)).extracting(r -> r.review().tier()).containsExactly("OFFICIAL", "ACADEMIC");
    assertThat(results).noneMatch(r -> r.url().contains("old") || r.url().contains("unrelated"));
    server.verify();
  }

  @Test void supplementaryFailureRetainsGeneralResultsAndStopsAtFourCalls() {
    var builder = RestClient.builder();
    var server = MockRestServiceServer.bindTo(builder).build();
    server.expect(requestTo("https://search.example/search")).andRespond(withSuccess(
        "{\"results\":[{\"title\":\"Java\",\"url\":\"https://example.org/java\"}]}", MediaType.APPLICATION_JSON));
    server.expect(requestTo("https://search.example/search")).andRespond(withServerError());
    emptyPasses(server, 2);
    var result = new TavilyWebSearchProvider("test-key", "https://search.example/search", builder,
        mock(HiddenResourceSourceRepository.class), passthrough()).search("Java", 5);
    assertThat(result).hasSize(1);
    server.verify();
  }
}
