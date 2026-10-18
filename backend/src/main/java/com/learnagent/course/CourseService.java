package com.learnagent.course;

import com.learnagent.document.CourseDocumentRepository;
import com.learnagent.rag.LocalKnowledgeService;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CourseService {
  private final CourseRepository courses; private final CourseDocumentRepository documents; private final LocalKnowledgeService knowledge;
  public CourseService(CourseRepository courses, CourseDocumentRepository documents, LocalKnowledgeService knowledge) { this.courses = courses; this.documents = documents; this.knowledge = knowledge; }
  @Transactional public Course create(String name, String description) { return courses.save(new Course(requiredName(name), normalize(description))); }
  @Transactional(readOnly = true) public List<Course> list() { return courses.findAll(); }
  @Transactional(readOnly = true) public Course get(UUID id) { return courses.findById(id).orElseThrow(() -> notFound(id)); }
  @Transactional public Course update(UUID id, String name, String description) { Course course = get(id); course.update(requiredName(name), normalize(description)); return course; }
  @Transactional public void delete(UUID id) { Course course = get(id); documents.findByCourseId(id).forEach(document -> knowledge.deleteDocumentVectors(document.getId())); courses.delete(course); }
  private String requiredName(String value) { if (value == null || value.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course name is required."); return value.trim(); }
  private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
  private ResponseStatusException notFound(UUID id) { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Course " + id + " was not found."); }
}
