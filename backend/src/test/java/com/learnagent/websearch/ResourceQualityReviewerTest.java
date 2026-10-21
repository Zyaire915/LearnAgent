package com.learnagent.websearch;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.learnagent.websearch.WebSearchProvider.*;

class ResourceQualityReviewerTest {
  private final ResourceQualityReviewer reviewer = new ResourceQualityReviewer();
  private final Instant now = Instant.parse("2026-10-19T00:00:00Z");
  private WebSearchResult result(String title, String url) {
    return new WebSearchResult(title, "Learning resource", url, "ignored display name", null);
  }

  @Test void relevantOfficialAndUniversityBeatCommunityAndUnknownWithoutMetrics() {
    var ranked = reviewer.rank("Java", List.of(result("Java", "https://unknown.example/a"),
        result("Java", "https://bilibili.com/video/1"), result("Java", "https://docs.oracle.com/java/"),
        result("Java", "https://ocw.mit.edu/java")), 10, now);
    assertThat(ranked).extracting(r -> r.review().tier()).containsExactly("OFFICIAL", "ACADEMIC", "COMMUNITY", "OTHER");
    assertThat(ranked).allSatisfy(r -> assertThat(r.review().reason()).contains("作者/机构信息未知").doesNotContain("互动数据", "可访问性"));
  }

  @Test void unrelatedOfficialDoesNotBeatRelevantCommunityAndSpoofIsNotTrusted() {
    var ranked = reviewer.rank("Java", List.of(result("Cooking", "https://docs.oracle.com/cooking"),
        result("Java", "https://csdn.net/java"), result("Java", "https://docs.oracle.com.evil.example/java")), 10, now);
    assertThat(ranked.getFirst().url()).isEqualTo("https://csdn.net/java");
    assertThat(ranked.get(1).review().tier()).isEqualTo("OTHER");
  }

  @Test void rankingIsStableAndRejectsUnsafeUrlsAndDeduplicatesTrackingLinksAndCopies() {
    String snippet = "This is a sufficiently long copied learning resource summary repeated on multiple sites.";
    List<WebSearchResult> input = new ArrayList<>(List.of(result("Java", "https://a.example/java?utm_source=x"),
        result("Java", "https://a.example/java#section"), result("Java", "javascript:alert(1)"),
        new WebSearchResult("Java", snippet, "https://b.example/java", "b", null),
        new WebSearchResult("Java", snippet, "https://c.example/java", "c", null)));
    var first = reviewer.rank("Java", input, 10, now);
    Collections.reverse(input);
    assertThat(reviewer.rank("Java", input, 10, now)).isEqualTo(first).hasSize(2);
  }

  @Test void authorsDatesAccessibilityAndCappedEngagementAffectScore() {
    var base = result("Java", "https://example.org/java");
    var strong = new WebSearchResult(base.title(), base.snippet(), base.url(), base.sourceName(), now,
        new Signals("Author", "University", true, Long.MAX_VALUE), null);
    int baseline = reviewer.rank("Java", List.of(base), 1, now).getFirst().review().score();
    var review = reviewer.rank("Java", List.of(strong), 1, now).getFirst().review();
    assertThat(review.score() - baseline).isEqualTo(13); // author 8, recent 5; obsolete metrics are ignored
    var inaccessible = new WebSearchResult("Java", "sponsored buy now", base.url(), "example.org", null,
        new Signals(null, null, false, null), null);
    var caution = reviewer.rank("Java", List.of(inaccessible), 1, now).getFirst().review();
    assertThat(caution.status()).isEqualTo("CAUTION");
    assertThat(caution.score()).isLessThan(baseline);
  }

  @Test void chineseTitlesMatchAndMissingOrFutureDatesDoNotGetFreshnessBonus() {
    var result = new WebSearchResult("操作系统课程", "", "https://example.org/os", "example.org", now.plusSeconds(1));
    var review = reviewer.rank("操作系统", List.of(result), 1, now).getFirst().review();
    assertThat(review.reason()).contains("标题 +40", "发布日期异常，晚于当前时间");
  }

  @Test void unknownAttributionIsNotTreatedAsVerifiedAbsenceOrAnUntrustedDomain() {
    var unknown = review("Java", result("Java", "https://unknown.example/java"));
    assertThat(unknown.score()).isEqualTo(40);
    assertThat(unknown.reason()).contains("未匹配已配置来源名单", "不加减分（不代表原文无作者）")
        .doesNotContain("未列入可信来源", "-8");
    var whitespace = new WebSearchResult("Java", "", "https://unknown.example/java", "unknown", null,
        new Signals("  ", "\t", null, null), null);
    assertThat(review("Java", whitespace).score()).isEqualTo(unknown.score());
    var named = new WebSearchResult("Java", "", whitespace.url(), "unknown", null,
        new Signals("Author", null, null, null), null);
    assertThat(review("Java", named).reason()).contains("署名不等于身份认证");
    assertThat(review("Java", named).score()).isEqualTo(48);
  }

  @Test void latinWordsDoNotMatchLongerWordsButWorkNextToChineseAndPunctuation() {
    var unrelated = review("Java", result("JavaScript 入门", "https://dev.java/js"));
    assertThat(unrelated.reason()).contains("均未匹配学习主题 -30");
    assertThat(unrelated.status()).isEqualTo("CAUTION");
    for (String title : List.of("Java集合", "JAVA: 集合", "Ｊａｖａ 集合")) {
      assertThat(review("Java集合", result(title, "https://example.org/java")).reason()).contains("标题 +40");
    }
    assertThat(review("Java", result("JavaScript Java", "https://example.org/java")).reason()).contains("标题 +40");
    assertThat(review("C++", result("C# guide", "https://example.org/csharp")).reason()).contains("均未匹配");
  }

  @Test void summaryIsLowerWeightAndDoesNotDoubleCountTitleMatches() {
    var title = new WebSearchResult("Java collections", "Java collections", "https://example.org/title", "example", null);
    var summary = new WebSearchResult("Tutorial", "Java collections", "https://example.org/summary", "example", null);
    var partial = new WebSearchResult("Java", "collections", "https://example.org/partial", "example", null);
    assertThat(review("Java collections", title).score()).isEqualTo(40);
    assertThat(review("Java collections", summary).score()).isEqualTo(10);
    assertThat(review("Java collections", partial).score()).isEqualTo(25);
    assertThat(review("the Java collections", title).score()).isEqualTo(40);
    assertThat(review("", title).reason()).contains("学习主题缺少可匹配词");
  }

  @Test void learningTopicsAndCopyrightNoticesAreNotPromotionalButDisclosuresAre() {
    for (String title : List.of("广告系统设计", "网页采集技术", "转载规范与版权", "Sponsored research methods")) {
      var review = review(title, new WebSearchResult(title, "未经许可禁止转载", "https://example.org/lesson", "example", null));
      assertThat(review.reason()).doesNotContain("检测到推广");
      assertThat(review.score()).isEqualTo(40);
    }
    for (String evidence : List.of("领取优惠券", "【广告内容】Java课程", "Sponsored content: Java", "转载自：某站", "采集自：某站")) {
      var review = review("Java", new WebSearchResult("Java", evidence, "https://example.org/promo", "example", null));
      assertThat(review.status()).isEqualTo("CAUTION");
      assertThat(review.reason()).contains("线索 -30", "需人工复核");
    }
  }

  @Test void missingMalformedFutureAndOldDatesHaveDifferentExplanations() {
    var base = result("Java", "https://example.org/java");
    assertThat(review("Java", base).reason()).contains("发布日期未知，提供方未返回");
    var invalid = new WebSearchResult("Java", "", base.url(), "example", null,
        new Signals(null, null, null, null, true), null);
    assertThat(review("Java", invalid).reason()).contains("发布日期格式异常");
    var future = new WebSearchResult("Java", "", base.url(), "example", now.plusSeconds(60));
    assertThat(review("Java", future).reason()).contains("晚于当前时间");
    var old = new WebSearchResult("Java", "", base.url(), "example", Instant.parse("2010-01-01T00:00:00Z"));
    assertThat(review("Java", old).reason()).contains("发布日期加分 +0").doesNotContain("日期异常", "日期未知");
    assertThat(List.of(base, invalid, future, old)).allSatisfy(r -> assertThat(review("Java", r).score()).isEqualTo(40));
  }

  @Test void invalidMetricsDoNotBecomeNegativePopularityAndMaximumIsNotAPercentage() {
    var invalid = new WebSearchResult("Java", "", "https://example.org/java", "example", null,
        new Signals(null, null, null, -100L), null);
    assertThat(review("Java", invalid).score()).isEqualTo(40);
    assertThat(review("Java", invalid).reason()).doesNotContain("互动数据", "可访问性");
    var best = new WebSearchResult("Java", "Java", "https://dev.java", "dev.java", now,
        new Signals("Author", "Institution", true, Long.MAX_VALUE), null);
    assertThat(review("Java", best).score()).isEqualTo(88); // 35 + 40 + 8 + 5
  }

  private ResourceReview review(String query, WebSearchResult result) {
    return reviewer.rank(query, List.of(result), 1, now).getFirst().review();
  }
}
