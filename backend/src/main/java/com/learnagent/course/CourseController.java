package com.learnagent.course;

import com.learnagent.api.CourseDtos.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/courses")
public class CourseController {
  private final CourseService service;private final CourseRepository courses;private final com.learnagent.document.CourseDocumentRepository documents;
  public CourseController(CourseService service,CourseRepository courses,com.learnagent.document.CourseDocumentRepository documents) { this.service = service;this.courses=courses;this.documents=documents; }
  @PostMapping public ResponseEntity<CourseResponse> create(@Valid @RequestBody CourseRequest request) { Course created = service.create(request.name(), request.description()); return ResponseEntity.status(HttpStatus.CREATED).body(CourseResponse.from(created)); }
  @GetMapping public List<CourseResponse> list() { return courses.findCourseSummaries().stream().map(CourseResponse::from).toList(); }
  @GetMapping("/{id}") public CourseResponse get(@PathVariable UUID id) { Course course=service.get(id);return response(course); }
  @PutMapping("/{id}") public CourseResponse update(@PathVariable UUID id, @Valid @RequestBody CourseRequest request) { return response(service.update(id, request.name(), request.description())); }
  @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id) { service.delete(id); }
  private CourseResponse response(Course course){return CourseResponse.from(course,documents.countByCourseId(course.getId()),documents.countByCourseIdAndStatus(course.getId(),com.learnagent.document.DocumentStatus.READY));}
}
