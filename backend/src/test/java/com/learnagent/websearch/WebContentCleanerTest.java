package com.learnagent.websearch;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class WebContentCleanerTest {
  @Test void removesReplacementCharactersNavigationNoiseAndDuplicateLines() {
    String cleaned = WebContentCleaner.clean("正文\uFFFD\n搜索\n搜索\n用户头像\n学习要点\n学习要点");
    assertThat(cleaned).contains("正文", "学习要点");
    assertThat(cleaned).doesNotContain("\uFFFD", "搜索", "用户头像");
    assertThat(cleaned.split("学习要点", -1)).hasSize(2);
  }

  @Test void stripsUnsafeHtmlButKeepsReadableText() {
    String cleaned = WebContentCleaner.clean("<script>alert(1)</script><b>TCP 慢启动</b>&nbsp;");
    assertThat(cleaned).contains("TCP 慢启动").doesNotContain("script", "alert(1)", "<b>");
  }
}
