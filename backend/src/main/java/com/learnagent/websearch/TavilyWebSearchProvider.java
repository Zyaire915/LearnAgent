package com.learnagent.websearch;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.learnagent.digest.HiddenResourceSource;
import com.learnagent.digest.HiddenResourceSourceRepository;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TavilyWebSearchProvider implements WebSearchProvider {
  private final String apiKey;
  private final String endpoint;
  private final RestClient client;
  private final HiddenResourceSourceRepository hidden;
  private final ArticleContentReviewer articles;

  @org.springframework.beans.factory.annotation.Autowired
  public TavilyWebSearchProvider(@Value("${learnagent.web-search.api-key:}") String apiKey,
      @Value("${learnagent.web-search.endpoint:https://api.tavily.com/search}") String endpoint,
      RestClient.Builder builder, HiddenResourceSourceRepository hidden, ArticleContentReviewer articles) {
    this.apiKey = apiKey;
    this.endpoint = endpoint;
    this.client = builder.build();
    this.hidden = hidden;
    this.articles = java.util.Objects.requireNonNull(articles);
  }

  @Override public boolean isConfigured() { return apiKey != null && !apiKey.isBlank(); }
  @Override public String providerName() { return "Tavily"; }

  @Override public List<WebSearchResult> search(String query, int limit) {
    return search(query, limit, List.of());
  }

  @Override public List<WebSearchResult> search(String query, int limit, List<WebSearchResult> history) {
    if (!isConfigured()) throw new WebSearchUnavailableException("WEB_SEARCH_NOT_CONFIGURED");
    if (query == null || query.isBlank()) throw new IllegalArgumentException("Search query is required.");
    int safeLimit = Math.max(1, Math.min(limit, 10));
    Set<String> blocked = new HashSet<>(hidden.findAll().stream().map(HiddenResourceSource::getHost).toList());
    List<WebSearchResult> candidates = new ArrayList<>();
    // Always give official documentation and courses a chance, even if general search is full.
    String searchText = ResourceQuery.searchText(query);
    List<SearchPass> passes = List.of(
        new SearchPass(searchText, List.of()),
        new SearchPass(searchText + " documentation 官方文档", ResourceReviewPolicy.OFFICIAL.stream().sorted().toList()),
        new SearchPass(searchText + " course 公开课程", ResourceReviewPolicy.COURSE_SEARCH),
        new SearchPass(searchText + " tutorial lecture 教程 讲义", List.of()));
    var topics = ResourceQuery.topics(query);
    if (topics.size() > 1) {
      List<SearchPass> splitPasses = new ArrayList<>();
      splitPasses.add(new SearchPass(query.trim(), List.of()));
      // Cover all explicit topics in at most three grouped requests, rather than six full-list searches.
      int batchSize = (topics.size() + 2) / 3;
      for (int start = 0; start < topics.size(); start += batchSize) {
        String part = String.join(" ", topics.subList(start, Math.min(start + batchSize, topics.size())));
        splitPasses.add(new SearchPass(part + " 课程 教程", List.of()));
      }
      passes = splitPasses;
    }
    int successful = 0;
    List<WebSearchResult> selected = List.of();
    for (int i = 0; i < passes.size(); i++) {
      if (topics.size() <= 1 && i == 3 && selected.size() >= safeLimit) break;
      SearchPass pass = passes.get(i);
      try {
        TavilyResponse response = client.post().uri(endpoint).header("Authorization", "Bearer " + apiKey)
            .body(new TavilyRequest(pass.query(), "basic", 10, "general", false, false, true, pass.domains()))
            .retrieve().body(TavilyResponse.class);
        successful++;
        if (response != null && response.results() != null) response.results().stream().filter(Objects::nonNull)
            .map(this::candidate).filter(item -> !blocked.contains(ResourceQualityReviewer.host(item.url())))
            .forEach(candidates::add);
      } catch (org.springframework.web.client.HttpClientErrorException exception) {
        // Authentication/quota/client failures will not improve by spending another request.
        break;
      } catch (org.springframework.web.client.RestClientException ignored) {
        // A failed supplementary pass must not discard results from another pass.
      }
      selected = ResourceSelection.select(query, candidates, history, safeLimit, Instant.now());
    }
    if (successful == 0) throw new WebSearchUnavailableException("WEB_SEARCH_FAILED: no search pass succeeded.");
    var shortlist = ResourceSelection.select(query, candidates, history, 10, Instant.now());
    var read = articles.review(query, shortlist);
    return ResourceSelection.select(query, read, history, safeLimit, Instant.now());
  }

  private record SearchPass(String query, List<String> domains) { }

  private WebSearchResult candidate(TavilyResult item) {
    Instant published = parse(item.publishedDate());
    boolean invalidDate = item.publishedDate() != null && !item.publishedDate().isBlank() && published == null;
    return new WebSearchResult(item.title() == null ? item.url() : item.title(), item.content() == null ? "" : item.content(), item.url(), ResourceQualityReviewer.host(item.url()),
        published, new Signals(item.author(), item.institution(), item.accessible(), item.interactions(), invalidDate), null);
  }

  private Instant parse(String value) {
    if (value == null || value.isBlank()) return null;
    value = value.trim();
    try { return Instant.parse(value); } catch (Exception ignored) { }
    try { return LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant(); } catch (Exception ignored) { }
    try { return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(); }
    catch (Exception ignored) { return null; }
  }

  private record TavilyRequest(String query, @JsonProperty("search_depth") String searchDepth,
      @JsonProperty("max_results") int maxResults, String topic,
      @JsonProperty("include_answer") boolean includeAnswer,
      @JsonProperty("include_raw_content") boolean includeRawContent,
      @JsonProperty("include_published_date") boolean includePublishedDate,
      @JsonProperty("include_domains") List<String> includeDomains) { }
  private record TavilyResponse(List<TavilyResult> results) { }
  // Optional metadata stays null when the provider does not supply it.
  private record TavilyResult(String title, String url, String content,
      @JsonProperty("published_date") String publishedDate, String author, String institution,
      Boolean accessible, Long interactions) { }
}
