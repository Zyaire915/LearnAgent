package com.learnagent.websearch;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.learnagent.websearch.WebSearchProvider.WebSearchResult;

class ResourceRecommendationRegressionTest {
  private WebSearchResult item(String title, String snippet, String url) {
    return new WebSearchResult(title, snippet, url, "source", null);
  }

  @Test void rejectsScreenshotNoiseAndUnrelatedTrustedDocumentsButKeepsRelevantCourses() {
    var selected = ResourceSelection.select("计算机网络", List.of(
        item("Solaris 10新增功能", "40 通信协议解析器实用程序.................40 SIP 端对端流量测量和日志记录................40",
            "https://docs.oracle.com/cd/E19253-01/819-0361/819-0361.pdf"),
        item("Python 說明文件內容", "IP网络的定义前缀、网络掩码和主机掩码网络对象运算符。", "https://docs.python.org/index"),
        item("羟3У", "ý王蘭ÝРиǵ ÒòЭ(4ʌħ) άЭHTTP ІЭFTP Э餘ετĹ ýúйϋЭUDPUDPик ЭTCPƬCPи",
            "https://home.ustc.edu.cn/notes.pdf"),
        item("计算机网络-华南理工大学", "spContent= 《计算机网络》是计算机类专业的必修课程。", "https://www.icourse163.org/course/network"),
        item("计算机网络课程讲义", "分层结构、路由、可靠传输和拥塞控制。", "https://ocw.mit.edu/network.pdf")),
        List.of(), 5, Instant.now());
    assertThat(selected).extracting(WebSearchResult::url).containsExactly(
        "https://ocw.mit.edu/network.pdf", "https://www.icourse163.org/course/network");
    assertThat(selected.get(1).snippet()).doesNotContain("spContent=");
  }

  @Test void trustedDomainCannotCompensateForSummaryOnlyOrTinyKeywordOverlap() {
    var selected = ResourceSelection.select("Java collections List Map Set", List.of(
        item("Solaris release notes", "Java collections List Map Set", "https://docs.oracle.com/solaris"),
        item("Java installation", "Installer", "https://docs.oracle.com/install"),
        item("Java collections List Map Set tutorial", "Examples", "https://unknown.example/java")),
        List.of(), 5, Instant.now());
    assertThat(selected).singleElement().satisfies(r -> assertThat(r.url()).contains("unknown.example"));
  }

  @Test void detectsCorruptionEvenWithRelevantTitleAndPreservesOrdinaryMultilingualText() {
    String corrupted = "ý王蘭ÝРиǵ ÒòЭ(4ʌħ) άЭHTTP ІЭFTP Э餘ετĹ ýúйϋЭUDPUDPик ЭTCPƬCPи";
    assertThat(ResourceTextQuality.issue("计算机网络", corrupted)).contains("乱码");
    assertThat(ResourceTextQuality.issue("Java", "文字��解码失败")).contains("乱码");
    assertThat(ResourceTextQuality.issue("Java", "Résumé français : méthodes, propriétés et héritage.")).isEmpty();
    assertThat(ResourceTextQuality.issue("计算机网络", "利用 α、β 和 λ 描述网络参数，HTTP 与 TCP 是常见协议。")).isEmpty();
    var selected = ResourceSelection.select("计算机网络", List.of(
        item("计算机网络", corrupted, "https://mit.edu/corrupt"),
        item("计算机网络", "章节一..........1 章节二..........8", "https://mit.edu/toc")), List.of(), 5, Instant.now());
    assertThat(selected).isEmpty();
  }
}
