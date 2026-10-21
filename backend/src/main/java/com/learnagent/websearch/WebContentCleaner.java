package com.learnagent.websearch;

import java.text.Normalizer;
import java.util.*;

public final class WebContentCleaner {
  private static final Set<String> NAVIGATION_NOISE = Set.of(
      "搜索", "写随笔", "我的博客", "短消息", "简洁模式", "用户头像", "返回主页",
      "登录", "注册", "首页", "上一页", "下一页");

  private WebContentCleaner() { }

  public static String clean(String raw) {
    if (raw == null || raw.isBlank()) return "";
    String normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC)
        .replace('\uFFFD', ' ')
        .replace('\u00A0', ' ')
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replaceAll("(?s)<script[^>]*>.*?</script>", " ")
        .replaceAll("(?s)<style[^>]*>.*?</style>", " ")
        .replaceAll("<[^>]+>", " ")
        .replaceAll("[\\p{Cc}&&[^\\r\\n\\t]]", " ");
    List<String> lines = new ArrayList<>();
    String previous = null;
    for (String sourceLine : normalized.split("\\R+")) {
      String line = sourceLine.replaceFirst("^\\s*spContent\\s*=\\s*", "").replaceAll("[ \\t]+", " ").trim();
      if (line.isBlank() || NAVIGATION_NOISE.contains(line) || line.equals(previous)) continue;
      lines.add(line);
      previous = line;
    }
    String result = String.join("\n", lines).replaceAll("\\n{3,}", "\n\n").trim();
    return result.length() <= 1600 ? result : result.substring(0, 1600) + "…";
  }
}
