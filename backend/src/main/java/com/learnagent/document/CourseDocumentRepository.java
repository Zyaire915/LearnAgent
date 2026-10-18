package com.learnagent.document;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseDocumentRepository extends JpaRepository<CourseDocument, UUID> {
  List<CourseDocument> findByCourseIdOrderByUploadedAtDesc(UUID courseId);
  List<CourseDocument> findByCourseId(UUID courseId);
  long countByCourseId(UUID courseId);
  long countByCourseIdAndStatus(UUID courseId, DocumentStatus status);
}
