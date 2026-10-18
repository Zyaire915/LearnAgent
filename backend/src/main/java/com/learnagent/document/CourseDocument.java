package com.learnagent.document;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "documents")
public class CourseDocument {
  @Id private UUID id;
  @Column(name = "course_id", nullable = false) private UUID courseId;
  @Column(nullable = false) private String filename;
  @Column(name = "original_filename", nullable = false) private String originalFilename;
  @Column(name = "content_type", nullable = false) private String contentType;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private DocumentStatus status;
  @Column(name = "error_message", length = 500) private String errorMessage;
  @Column(name = "uploaded_at", nullable = false) private Instant uploadedAt;
  @Column(name = "processed_at") private Instant processedAt;
  protected CourseDocument() { }
  public CourseDocument(UUID courseId, String filename, String originalFilename, String contentType) {
    this.id = UUID.randomUUID(); this.courseId = courseId; this.filename = filename; this.originalFilename = originalFilename;
    this.contentType = contentType; this.status = DocumentStatus.UPLOADED; this.uploadedAt = Instant.now();
  }
  public UUID getId() { return id; } public UUID getCourseId() { return courseId; } public String getFilename() { return filename; }
  public String getOriginalFilename() { return originalFilename; } public String getContentType() { return contentType; }
  public DocumentStatus getStatus() { return status; } public String getErrorMessage() { return errorMessage; }
  public Instant getUploadedAt() { return uploadedAt; } public Instant getProcessedAt() { return processedAt; }
  public void processing() { status = DocumentStatus.PROCESSING; errorMessage = null; }
  public void ready() { status = DocumentStatus.READY; errorMessage = null; processedAt = Instant.now(); }
  public void failed(String message) { status = DocumentStatus.FAILED; errorMessage = message; processedAt = Instant.now(); }
}
