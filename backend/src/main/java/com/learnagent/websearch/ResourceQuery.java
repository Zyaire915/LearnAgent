package com.learnagent.websearch;

import java.text.Normalizer;
import java.util.*;

/** Small, explicit query rules; no model-generated synonyms or unlimited fan-out. */
final class ResourceQuery {
  private ResourceQuery() { }
  static List<String> topics(String query) {
    String value = query == null ? "" : query.trim();
    String separator = value.matches(".*[、,，;；].*") ? "[、,，;；]|以及|及" : "[、,，;；]";
    return Arrays.stream(value.split(separator)).map(String::trim).filter(s -> !s.isEmpty()).distinct().limit(12).toList();
  }

  static String normalize(String value) {
    String text = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
        .replaceAll("\\barithmetic\\s+(?:and\\s+)?logic(?:al)?\\s+unit\\b|算术逻辑运算单元|算术逻辑单元|算术逻辑部件", " alu ")
        .replaceAll("\\bcentral\\s+processing\\s+unit\\b|中央处理器", " cpu ");
    if (text.matches("(?s).*\\b(?:alu|cpu)\\b.*") || text.contains("alu") || text.contains("cpu"))
      text = text.replace("计算机", " ").replace("工作原理", " ").replace("详解", " ");
    return text;
  }

  static String searchText(String query) {
    String normalized = normalize(query);
    if (normalized.contains("alu")) return normalized.trim() + " 算术逻辑单元 arithmetic logic unit";
    if (normalized.contains("cpu")) return normalized.trim() + " 中央处理器 central processing unit";
    return query.trim();
  }
}
