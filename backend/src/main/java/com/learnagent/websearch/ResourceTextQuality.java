package com.learnagent.websearch;

import java.util.regex.Pattern;

/** Conservative extraction-quality checks; never attempt to invent decoded text. */
final class ResourceTextQuality {
  private ResourceTextQuality() { }
  static String issue(String title, String snippet) {
    if (WebContentCleaner.clean(title).isBlank()) return "标题缺失，无法判断资料主题";
    if (garbled(title) || garbled(snippet)) return "文本疑似乱码或编码损坏";
    String text = snippet == null ? "" : snippet;
    if (Pattern.compile("\\.{6,}|…{3,}|·{6,}").matcher(text).results().count() >= 2)
      return "摘要主要含目录引导符，缺少可用的正文证据";
    return "";
  }

  private static boolean garbled(String text) {
    if (text == null || text.isEmpty()) return false;
    long replacements = text.codePoints().filter(c -> c == 0xfffd).count();
    long controls = text.codePoints().filter(c -> c >= 0x80 && c <= 0x9f).count();
    if (replacements >= 2 || controls >= 2) return true;
    long latinExtended = text.codePoints().filter(c -> Character.isLetter(c) && c > 127
        && Character.UnicodeScript.of(c) == Character.UnicodeScript.LATIN).count();
    long greekCyrillic = text.codePoints().filter(c -> Character.isLetter(c)
        && (Character.UnicodeScript.of(c) == Character.UnicodeScript.GREEK
            || Character.UnicodeScript.of(c) == Character.UnicodeScript.CYRILLIC)).count();
    long letters = text.codePoints().filter(Character::isLetter).count();
    // Mixed mojibake in PDF extraction; ordinary accented text or Greek mathematics alone is allowed.
    return latinExtended >= 4 && greekCyrillic >= 4
        && (latinExtended + greekCyrillic) > letters * 0.3;
  }
}
