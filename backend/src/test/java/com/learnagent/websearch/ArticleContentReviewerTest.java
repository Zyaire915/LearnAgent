package com.learnagent.websearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnagent.websearch.WebSearchProvider.*;
import com.learnagent.websearch.ArticleContentReviewer.Verdict;

class ArticleContentReviewerTest {
  private static final Instant NOW = Instant.parse("2026-10-21T00:00:00Z");
  private static final String QUOTE = "Java ArrayList uses a resizable array; indexed lookup is constant time, while insertion may require shifting elements.";
  private static final String BODY = "Author: Alice\nPublished: 2025-01-02\n" + QUOTE + "\n" +
      "An insertion in the middle moves the later elements one position to the right. This takes linear time in the worst case.";
  private static final WebSearchResult CANDIDATE = new WebSearchResult("Java ArrayList", "Search snippet", "https://example.org/java", "example.org", NOW,
      new Signals("Invented search author", null, true, 999999L), null);
  private Verdict verdict(String author, String date, String quote) {
    return new Verdict(true, "Java ArrayList介绍动态数组、随机访问与插入开销。", "给出数据结构及操作复杂度解释", quote,
        author, "", date, "Author: Alice", "", "Published: 2025-01-02");
  }

  @Test void requiresActualBodyQuotesAndReplacesSearchMetadata() {
    var read = ArticleContentReviewer.grounded(CANDIDATE, BODY, verdict("Alice", "2025-01-02", QUOTE), NOW);
    assertThat(read.signals().author()).isEqualTo("Alice");
    assertThat(read.publishedAt()).isEqualTo("2025-01-02T00:00:00Z");
    var selected = ResourceSelection.select("Java ArrayList", List.of(read), List.of(), 5, NOW);
    assertThat(selected).singleElement().satisfies(r -> {
      assertThat(r.review().status()).isEqualTo("BODY_REVIEWED");
      assertThat(r.review().reason()).contains(QUOTE, "原文作者：Alice", "2025-01-02", "正文抓取时间").doesNotContain("互动", "可访问");
    });
    assertThat(ArticleContentReviewer.grounded(CANDIDATE, BODY, verdict("Alice", "2025-01-02", "An invented statement which does not actually occur in this article body."), NOW)).isNull();
  }

  @Test void hallucinatedAndMissingMetadataDoNotBecomeFacts() {
    var read = ArticleContentReviewer.grounded(CANDIDATE, BODY, verdict("Bob", "2025-01-03", QUOTE), NOW);
    assertThat(read.signals().author()).isNull();
    assertThat(read.publishedAt()).isNull();
    assertThat(read.signals().bodyEvidence()).contains("未提取到明确作者", "未提取到有效发布日期");
    var rejected = new Verdict(false, "Summary", "not educational", QUOTE, "", "", "", "", "", "");
    assertThat(ArticleContentReviewer.grounded(CANDIDATE, BODY, rejected, NOW)).isNull();
  }

  @Test @SuppressWarnings("unchecked") void extractionFeedsWholePageToModelAndDoesNotFallbackToSnippets() throws Exception {
    var http = RestClient.builder();
    var server = MockRestServiceServer.bindTo(http).build();
    ObjectProvider<ChatClient.Builder> provider = mock(ObjectProvider.class);
    var model = mock(ChatClient.Builder.class, RETURNS_DEEP_STUBS);
    doReturn(model).when(model).clone();
    when(provider.getIfAvailable()).thenReturn(model);
    when(model.clone().build().prompt().system(anyString()).user(anyString()).call().entity(Verdict.class))
        .thenReturn(verdict("Alice", "2025-01-02", QUOTE));
    String response = new ObjectMapper().writeValueAsString(java.util.Map.of("results", List.of(java.util.Map.of("url", CANDIDATE.url(), "raw_content", BODY))));
    server.expect(requestTo("https://extract.example/extract"))
        .andExpect(jsonPath("$.query").doesNotExist()).andExpect(jsonPath("$.urls[0]").value(CANDIDATE.url()))
        .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    server.expect(requestTo("https://extract.example/extract")).andRespond(withServerError());
    var service = new ArticleContentReviewer("test-key", "https://extract.example/extract", http.build(), provider);
    assertThat(service.review("Java", List.of(CANDIDATE))).hasSize(1);
    verify(model.clone().build().prompt().system(anyString())).user(contains(BODY));
    assertThat(service.review("Java", List.of(CANDIDATE))).isEmpty();
    server.verify();
  }

  @Test @SuppressWarnings("unchecked") void oversizedOrFailedExtractionDoesNotRunModel() throws Exception {
    var http = RestClient.builder();
    var server = MockRestServiceServer.bindTo(http).build();
    ObjectProvider<ChatClient.Builder> provider = mock(ObjectProvider.class);
    var model = mock(ChatClient.Builder.class);
    when(provider.getIfAvailable()).thenReturn(model);
    var response = new ObjectMapper().writeValueAsString(java.util.Map.of("results", List.of(java.util.Map.of("url", CANDIDATE.url(), "raw_content", "x".repeat(24001)))));
    server.expect(requestTo("https://extract.example/extract")).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    assertThat(new ArticleContentReviewer("test-key", "https://extract.example/extract", http.build(), provider).review("Java", List.of(CANDIDATE))).isEmpty();
    verifyNoInteractions(model);
    server.verify();
  }
}
