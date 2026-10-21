package com.learnagent.websearch;
import java.time.Instant;import java.util.List;
public interface WebSearchProvider {
  List<WebSearchResult> search(String query, int limit);
  default List<WebSearchResult> search(String query, int limit, List<WebSearchResult> history) {
    return ResourceSelection.select(query, search(query, 10), history, limit, Instant.now());
  }
  boolean isConfigured();
  String providerName();
  record WebSearchResult(String title, String snippet, String url, String sourceName,
                         Instant publishedAt, Signals signals, ResourceReview review) {
    public WebSearchResult(String title, String snippet, String url, String sourceName, Instant publishedAt) {
      this(title, snippet, url, sourceName, publishedAt, null, null);
    }
    public WebSearchResult reviewed(ResourceReview value) {
      return new WebSearchResult(title, snippet, url, sourceName, publishedAt, signals, value);
    }
  }
  // Only provider-supplied evidence belongs here; missing data remains null.
  record Signals(String author, String institution, Boolean accessible, Long interactions, boolean invalidPublishedDate, String bodyEvidence) {
    public Signals(String author, String institution, Boolean accessible, Long interactions, boolean invalidPublishedDate) {
      this(author, institution, accessible, interactions, invalidPublishedDate, null);
    }
    public Signals(String author, String institution, Boolean accessible, Long interactions) {
      this(author, institution, accessible, interactions, false);
    }
  }
}
