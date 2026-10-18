package com.learnagent.document;

import com.learnagent.api.CourseDtos.DocumentResponse;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api/courses/{courseId}/documents")
public class DocumentController {
  private final DocumentService service;
  public DocumentController(DocumentService service) { this.service = service; }
  @GetMapping public List<DocumentResponse> list(@PathVariable UUID courseId) { return service.list(courseId).stream().map(DocumentResponse::from).toList(); }
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE) public ResponseEntity<DocumentResponse> upload(@PathVariable UUID courseId, @RequestPart("file") MultipartFile file) { CourseDocument document = service.upload(courseId, file); return ResponseEntity.status(HttpStatus.CREATED).body(DocumentResponse.from(document)); }
  @DeleteMapping("/{documentId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID courseId, @PathVariable UUID documentId) { service.delete(courseId, documentId); }
}
