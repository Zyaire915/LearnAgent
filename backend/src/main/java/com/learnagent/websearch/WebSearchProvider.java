package com.learnagent.websearch;
import java.time.Instant;import java.util.List;
public interface WebSearchProvider{List<WebSearchResult> search(String query,int limit);boolean isConfigured();String providerName();record WebSearchResult(String title,String snippet,String url,String sourceName,Instant publishedAt){}}
