package com.learnagent.rag;

import java.util.*;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

@Component
public class SourceMapper {
  public List<RagSource> mapAndDeduplicate(List<Document> documents) {
    Map<String, RagSource> unique = new LinkedHashMap<>();
    for (Document document : documents) {
      Map<String, Object> metadata = document.getMetadata();
      UUID documentId = UUID.fromString(String.valueOf(metadata.get("documentId")));
      Integer page = metadata.get("pageNumber") instanceof Number value ? value.intValue() : null;
      String key = documentId + ":" + (page == null ? document.getId() : page);
      unique.putIfAbsent(key, new RagSource(documentId, String.valueOf(metadata.get("filename")), page, snippet(document.getText()), document.getScore()));
    }
    return List.copyOf(unique.values());
  }
  private String snippet(String text) { String compact = text == null ? "" : text.replaceAll("\\s+", " ").trim(); return compact.length() <= 280 ? compact : compact.substring(0, 277) + "..."; }
}
