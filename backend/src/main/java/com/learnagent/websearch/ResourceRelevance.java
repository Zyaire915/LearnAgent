package com.learnagent.websearch;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import static com.learnagent.websearch.ResourceReviewPolicy.*;

/** Latin whole-token matching and Chinese bigrams, with lower-weight summary evidence. */
final class ResourceRelevance {
  private static final Pattern TOKEN = Pattern.compile("[\\p{IsHan}]+|[a-z0-9]+(?:\\+\\+|#)?");
  private ResourceRelevance() { }

  static Match evaluate(String query, String title, String summary) {
    var topics = ResourceQuery.topics(query);
    if (topics.size() > 1) {
      Match best = topics.stream().map(topic -> evaluateTopic(topic, title, summary))
          .max(Comparator.comparing(Match::sufficient).thenComparingInt(Match::score)).orElseThrow();
      return new Match(best.score(), best.unrelated(), best.sufficient(), "按子主题匹配；" + best.reason());
    }
    return evaluateTopic(query, title, summary);
  }

  static Match evaluateTopic(String query, String title, String summary) {
    Set<String> wanted = tokens(query), titleTokens = tokens(title), summaryTokens = tokens(summary);
    for (String token : wanted) {
      if (token.equals("树")) {
        if ((title == null ? "" : title).contains("树")) titleTokens.add(token);
        if ((summary == null ? "" : summary).contains("树")) summaryTokens.add(token);
      } else if (token.equals("图")) {
        String graph = ".*(图论|图结构|图的|图算法|图遍历|图搜索|有向图|无向图|^图$|^图[：: ]).*";
        if ((title == null ? "" : title).matches(graph)) titleTokens.add(token);
        if ((summary == null ? "" : summary).matches(graph)) summaryTokens.add(token);
      }
    }
    if (wanted.isEmpty()) return new Match(0, false, false, "学习主题缺少可匹配词，不加分");
    long titleHits = wanted.stream().filter(titleTokens::contains).count();
    long summaryOnlyHits = wanted.stream().filter(t -> !titleTokens.contains(t) && summaryTokens.contains(t)).count();
    if (titleHits + summaryOnlyHits == 0) return new Match(NO_RELEVANCE, true, false, "标题及摘要均未匹配学习主题 " + NO_RELEVANCE);
    int titlePoints = (int) (TITLE_RELEVANCE * titleHits / wanted.size());
    int summaryPoints = (int) (SUMMARY_RELEVANCE * summaryOnlyHits / wanted.size());
    boolean sufficient = titleHits / (double) wanted.size() >= 0.30
        && (titleHits + summaryOnlyHits) / (double) wanted.size() >= 0.40;
    return new Match(titlePoints + summaryPoints, false, sufficient,
        "主题相关性：标题 +" + titlePoints + "，摘要补充 +" + summaryPoints);
  }

  private static Set<String> tokens(String value) {
    Set<String> tokens = new HashSet<>();
    String normalized = ResourceQuery.normalize(value);
    var matcher = TOKEN.matcher(normalized);
    while (matcher.find()) {
      String token = matcher.group();
      if (Character.UnicodeScript.of(token.codePointAt(0)) == Character.UnicodeScript.HAN) {
        int[] points = token.codePoints().toArray();
        if (points.length == 1) tokens.add(token);
        for (int i = 0; i + 1 < points.length; i++) tokens.add(new String(points, i, 2));
      } else if (!ENGLISH_STOP_WORDS.contains(token)) tokens.add(token);
    }
    return tokens;
  }

  record Match(int score, boolean unrelated, boolean sufficient, String reason) { }
}
