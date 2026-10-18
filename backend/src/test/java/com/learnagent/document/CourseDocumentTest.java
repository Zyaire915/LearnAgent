package com.learnagent.document;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CourseDocumentTest {
  @Test void tracks_successful_and_failed_processing_states() {
    CourseDocument document = new CourseDocument(UUID.randomUUID(), "lecture.pdf", "lecture.pdf", "application/pdf");
    assertThat(document.getStatus()).isEqualTo(DocumentStatus.UPLOADED);
    document.processing(); assertThat(document.getStatus()).isEqualTo(DocumentStatus.PROCESSING);
    document.ready(); assertThat(document.getStatus()).isEqualTo(DocumentStatus.READY); assertThat(document.getProcessedAt()).isNotNull();
    document.failed("Unreadable PDF"); assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED); assertThat(document.getErrorMessage()).isEqualTo("Unreadable PDF");
  }
}
