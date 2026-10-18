package com.learnagent.api;

import com.learnagent.course.Course;
import com.learnagent.course.CourseRepository;
import com.learnagent.document.CourseDocument;
import com.learnagent.document.DocumentStatus;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

public final class CourseDtos {
  private CourseDtos() { }
  public record CourseRequest(@NotBlank(message = "Course name is required.") String name, String description) { }
  public record CourseResponse(UUID id, String name, String description, Instant createdAt, Instant updatedAt,long documentCount,long readyDocumentCount) {
    public static CourseResponse from(Course course) { return new CourseResponse(course.getId(), course.getName(), course.getDescription(), course.getCreatedAt(), course.getUpdatedAt(),0,0); }
    public static CourseResponse from(CourseRepository.CourseSummaryProjection course) { return new CourseResponse(course.getId(),course.getName(),course.getDescription(),course.getCreatedAt(),course.getUpdatedAt(),course.getDocumentCount(),course.getReadyDocumentCount()); }
    public static CourseResponse from(Course course,long documents,long ready){return new CourseResponse(course.getId(),course.getName(),course.getDescription(),course.getCreatedAt(),course.getUpdatedAt(),documents,ready);}
  }
  public record DocumentResponse(UUID id, UUID courseId, String filename, String originalFilename, String contentType,
      DocumentStatus status, String errorMessage, Instant uploadedAt, Instant processedAt) {
    public static DocumentResponse from(CourseDocument document) { return new DocumentResponse(document.getId(), document.getCourseId(), document.getFilename(), document.getOriginalFilename(), document.getContentType(), document.getStatus(), document.getErrorMessage(), document.getUploadedAt(), document.getProcessedAt()); }
  }
}
