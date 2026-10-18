package com.learnagent.rag;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class SourceMapperTest {
  @Test void maps_real_metadata_and_deduplicates_document_page() {
    UUID documentId = UUID.randomUUID(); Map<String, Object> metadata = Map.of("documentId", documentId.toString(), "filename", "os.pdf", "pageNumber", 4);
    List<RagSource> sources = new SourceMapper().mapAndDeduplicate(List.of(new Document("First relevant chunk", metadata), new Document("Second chunk on same page", metadata)));
    assertThat(sources).singleElement().satisfies(source -> { assertThat(source.documentId()).isEqualTo(documentId); assertThat(source.filename()).isEqualTo("os.pdf"); assertThat(source.pageNumber()).isEqualTo(4); assertThat(source.snippet()).contains("First relevant chunk"); });
  }
}
