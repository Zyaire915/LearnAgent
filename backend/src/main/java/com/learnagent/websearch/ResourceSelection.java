package com.learnagent.websearch;

import java.net.URI;
import java.text.Normalizer;
import java.time.Instant;
import java.util.*;
import com.learnagent.websearch.WebSearchProvider.WebSearchResult;

/** Shared selection for search results and persisted recommendation history. */
public final class ResourceSelection {
  private ResourceSelection() { }

  public static String canonicalUrl(String url) {
    if (ResourceQualityReviewer.host(url).isEmpty()) return "";
    URI uri = URI.create(url).normalize();
    String query = uri.getRawQuery();
    String kept = query == null ? "" : Arrays.stream(query.split("&"))
        .filter(p -> !p.toLowerCase(Locale.ROOT).matches("(utm_[^=]*|fbclid|gclid)=.*"))
        .sorted().reduce((a, b) -> a + "&" + b).orElse("");
    int port = uri.getPort();
    boolean defaultPort = port == -1 || (port == 80 && "http".equalsIgnoreCase(uri.getScheme()))
        || (port == 443 && "https".equalsIgnoreCase(uri.getScheme()));
    return ResourceQualityReviewer.host(url) + (defaultPort ? "" : ":" + port)
        + uri.getRawPath().replaceFirst("/+$", "") + "?" + kept;
  }

  public static List<WebSearchResult> select(String query, List<WebSearchResult> candidates,
      List<WebSearchResult> history, int limit, Instant now) {
    Set<String> urls = new HashSet<>();
    List<Set<String>> summaries = new ArrayList<>();
    for (var old : history) {
      urls.add(canonicalUrl(old.url()));
      var signature = signature(old.snippet());
      if (!signature.isEmpty()) summaries.add(signature);
    }
    List<WebSearchResult> pool = new ArrayList<>(new ResourceQualityReviewer().rank(query, candidates, candidates.size(), now)
        .stream().filter(item -> "AUTO_REVIEWED".equals(item.review().status()) || "BODY_REVIEWED".equals(item.review().status())).toList());
    List<WebSearchResult> selected = new ArrayList<>();
    Map<String, Integer> sourceCounts = new HashMap<>();
    Set<String> covered = new HashSet<>();
    var topics = ResourceQuery.topics(query);
    while (!pool.isEmpty() && selected.size() < Math.max(1, Math.min(limit, 10))) {
      pool.removeIf(item -> {
        var sig = signature(item.snippet());
        return urls.contains(canonicalUrl(item.url())) || sourceCounts.getOrDefault(sourceGroup(item.url()), 0) >= 2
            || (!sig.isEmpty() && summaries.stream().anyMatch(old -> similar(old, sig)))
            || selected.stream().anyMatch(old -> sameSourceCopy(old, item));
      });
      if (pool.isEmpty()) break;
      // Keep score order within each priority bucket; first spread across sources and uncovered topics.
      WebSearchResult best = pool.stream().max(Comparator.comparingInt(item -> {
        boolean newSource = sourceCounts.getOrDefault(sourceGroup(item.url()), 0) == 0;
        boolean newTopic = topics.stream().anyMatch(t -> !covered.contains(t)
            && ResourceRelevance.evaluateTopic(t, item.title(), item.snippet()).sufficient());
        return (newSource ? 2 : 0) + (newTopic ? 1 : 0);
      })).orElseThrow();
      pool.remove(best);
      selected.add(best);
      urls.add(canonicalUrl(best.url()));
      var sig = signature(best.snippet());
      if (!sig.isEmpty()) summaries.add(sig);
      sourceCounts.merge(sourceGroup(best.url()), 1, Integer::sum);
      topics.stream().filter(t -> ResourceRelevance.evaluateTopic(t, best.title(), best.snippet()).sufficient()).forEach(covered::add);
    }
    return List.copyOf(selected);
  }

  static String sourceGroup(String url) {
    String host = ResourceQualityReviewer.host(url);
    // Group known institutions/platforms before the conservative domain suffix fallback.
    for (var domains : List.of(ResourceReviewPolicy.OFFICIAL, ResourceReviewPolicy.ACADEMIC,
        ResourceReviewPolicy.EDUCATION, ResourceReviewPolicy.COMMUNITY)) {
      for (String domain : domains) if (host.equals(domain) || host.endsWith("." + domain)) {
        if (domain.equals("youtu.be")) return "youtube.com";
        host = domain;
        break;
      }
    }
    String[] labels = host.split("\\.");
    if (labels.length <= 2) return host;
    String suffix = labels[labels.length - 2] + "." + labels[labels.length - 1];
    Set<String> compound = Set.of("com.cn", "edu.cn", "org.cn", "ac.uk", "co.uk", "org.uk", "edu.au", "com.au",
        "ac.jp", "co.jp", "ac.in", "co.in", "edu.sg", "com.sg", "edu.hk", "ac.kr", "co.za", "ac.za", "com.br");
    return compound.contains(suffix) ? labels[labels.length - 3] + "." + suffix : suffix;
  }

  private static boolean sameSourceCopy(WebSearchResult a, WebSearchResult b) {
    if (!sourceGroup(a.url()).equals(sourceGroup(b.url()))) return false;
    String first = normalized(a.title()), second = normalized(b.title());
    if (first.length() >= 6 && first.equals(second)) return true;
    var left = signature(a.snippet());
    var right = signature(b.snippet());
    return !left.isEmpty() && !right.isEmpty() && overlap(left, right) >= 0.60;
  }

  private static String normalized(String text) {
    return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC)
        .toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{Z}\\s]", "");
  }

  // Conservative near-copy detection; short/absent snippets cannot establish duplicates.
  private static Set<String> signature(String text) {
    String normalized = normalized(text);
    int[] points = normalized.codePoints().toArray();
    if (points.length < 40) return Set.of();
    Set<String> grams = new HashSet<>();
    for (int i = 0; i + 4 < points.length; i++) grams.add(new String(points, i, 5));
    return grams;
  }
  private static boolean similar(Set<String> a, Set<String> b) {
    return overlap(a, b) >= 0.85;
  }
  private static double overlap(Set<String> a, Set<String> b) {
    long common = a.stream().filter(b::contains).count();
    return common / (double) (a.size() + b.size() - common);
  }
}
