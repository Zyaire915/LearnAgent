package com.learnagent.websearch;

import java.net.URI;
import java.time.*;
import java.util.*;
import static com.learnagent.websearch.ResourceReviewPolicy.*;
import com.learnagent.websearch.WebSearchProvider.WebSearchResult;

/** Deterministic heuristics, not a claim of manual verification. */
public final class ResourceQualityReviewer {

  public static String host(String url) {
    try {
      URI uri = URI.create(url);
      if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
          || uri.getUserInfo() != null || uri.getHost() == null) return "";
      return uri.getHost().toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
    } catch (RuntimeException ignored) { return ""; }
  }

  public List<WebSearchResult> rank(String query, List<WebSearchResult> results, int limit, Instant now) {
    List<WebSearchResult> ranked = results.stream().filter(Objects::nonNull)
        .filter(r -> !host(r.url()).isEmpty()).map(r -> new WebSearchResult(WebContentCleaner.clean(r.title()), WebContentCleaner.clean(r.snippet()),
            r.url(), r.sourceName(), r.publishedAt(), r.signals(), review(query, r, now)))
        .sorted(Comparator.<WebSearchResult>comparingInt(r -> r.review().score()).reversed()
            .thenComparing(WebSearchResult::url)).toList();
    Set<String> urls = new HashSet<>(), contents = new HashSet<>();
    return ranked.stream().filter(r -> {
      String content = text(r.snippet()).replaceAll("\\s+", "");
      return urls.add(ResourceSelection.canonicalUrl(r.url())) && (content.length() < MIN_DUPLICATE_SUMMARY_LENGTH || contents.add(content));
    }).limit(Math.max(1, Math.min(limit, 100))).toList();
  }

  private ResourceReview review(String query, WebSearchResult r, Instant now) {
    String host = host(r.url());
    boolean official = matches(host, OFFICIAL);
    boolean academic = matches(host, ACADEMIC);
    String tier = official ? "OFFICIAL" : academic ? "ACADEMIC" : matches(host, EDUCATION) ? "EDUCATION" : matches(host, COMMUNITY) ? "COMMUNITY" : "OTHER";
    int score = official || academic ? TRUSTED_DOMAIN : tier.equals("EDUCATION") ? EDUCATION_DOMAIN : tier.equals("COMMUNITY") ? COMMUNITY_DOMAIN : 0;
    List<String> reasons = new ArrayList<>();
    reasons.add("规则 " + VERSION);
    reasons.add(official ? "匹配官方来源名单 +" + TRUSTED_DOMAIN : academic ? "匹配高校来源名单 +" + TRUSTED_DOMAIN
        : tier.equals("EDUCATION") ? "匹配开放教育平台 +" + EDUCATION_DOMAIN + "（课程质量仍按具体内容判断）" : tier.equals("COMMUNITY") ? "匹配知名社区 +" + COMMUNITY_DOMAIN + "（不代表文章质量已核实）" : "未匹配已配置来源名单，域名不加分");
    var relevance = ResourceRelevance.evaluate(query, WebContentCleaner.clean(r.title()), WebContentCleaner.clean(r.snippet()));
    score += relevance.score();
    reasons.add(relevance.reason());
    if (!relevance.sufficient()) reasons.add("主题匹配证据不足：标题需覆盖至少30%的主题词，标题与摘要合计至少40%；不纳入推荐");
    String textIssue = ResourceTextQuality.issue(r.title(), r.snippet());
    if (!textIssue.isEmpty()) reasons.add(textIssue + "；不纳入推荐");
    var signals = r.signals();
    boolean bodyReviewed = signals != null && signals.bodyEvidence() != null;
    boolean attributed = signals != null && (!text(signals.author()).isBlank() || !text(signals.institution()).isBlank());
    score += attributed ? ATTRIBUTION : 0;
    reasons.add(attributed ? (bodyReviewed ? "原文署名/发布机构信息 +" : "提供方返回作者/机构信息 +") + ATTRIBUTION + "（署名不等于身份认证）"
        : "作者/机构信息未知，不加减分（不代表原文无作者）");
    if (signals != null && signals.invalidPublishedDate()) {
      reasons.add("提供方发布日期格式异常，无法解析，不加分");
    } else if (r.publishedAt() == null) {
      reasons.add(bodyReviewed ? "发布日期未知，抓取正文无有效依据，不加分" : "发布日期未知，提供方未返回，不加分");
    } else if (r.publishedAt().isAfter(now)) {
      reasons.add("发布日期异常，晚于当前时间，不加分");
    } else {
      long days = Duration.between(r.publishedAt(), now).toDays();
      int freshness = days <= RECENT_DAYS ? RECENT_DATE : days <= OLDER_DAYS ? OLDER_DATE : 0;
      score += freshness; reasons.add((bodyReviewed ? "原文" : "提供方") + "发布日期加分 +" + freshness);
    }
    if (bodyReviewed) reasons.add(signals.bodyEvidence());
    boolean promotional = PROMOTION.matcher(text(r.title())).find() || PROMOTION.matcher(text(r.snippet())).find();
    if (promotional) { score += PROMOTIONAL; reasons.add("检测到推广或转载/采集声明线索 " + PROMOTIONAL + "（规则判断，需人工复核）"); }
    return new ResourceReview(score, tier, score < CAUTION_THRESHOLD || !relevance.sufficient() || !textIssue.isEmpty() || promotional
        ? "CAUTION" : bodyReviewed ? "BODY_REVIEWED" : "AUTO_REVIEWED", String.join("；", reasons));
  }

  private static boolean matches(String host, Set<String> domains) {
    return domains.stream().anyMatch(d -> host.equals(d) || host.endsWith("." + d));
  }
  private static String text(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT); }
}
