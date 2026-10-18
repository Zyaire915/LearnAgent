package com.learnagent.course;

import java.util.UUID;
import java.util.Optional;
import java.util.List;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CourseRepository extends JpaRepository<Course, UUID> {
  Optional<Course> findByName(String name);
  @Query(value="""
      SELECT c.id AS id, c.name AS name, c.description AS description,
             c.created_at AS createdAt, c.updated_at AS updatedAt,
             COUNT(d.id) AS documentCount,
             COUNT(d.id) FILTER (WHERE d.status = 'READY') AS readyDocumentCount
      FROM courses c LEFT JOIN documents d ON d.course_id = c.id
      GROUP BY c.id, c.name, c.description, c.created_at, c.updated_at
      ORDER BY c.created_at DESC
      """,nativeQuery=true)
  List<CourseSummaryProjection> findCourseSummaries();
  interface CourseSummaryProjection{UUID getId();String getName();String getDescription();Instant getCreatedAt();Instant getUpdatedAt();long getDocumentCount();long getReadyDocumentCount();}
}
