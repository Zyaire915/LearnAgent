package com.learnagent.document;

import com.learnagent.course.CourseService;
import com.learnagent.rag.*;
import java.io.IOException;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DocumentService {
  private final CourseService courses; private final CourseDocumentRepository documents; private final LocalKnowledgeService knowledge;
  public DocumentService(CourseService courses, CourseDocumentRepository documents, LocalKnowledgeService knowledge) { this.courses = courses; this.documents = documents; this.knowledge = knowledge; }
  @Transactional(readOnly = true) public List<CourseDocument> list(UUID courseId) { courses.get(courseId); return documents.findByCourseIdOrderByUploadedAtDesc(courseId); }
  @Transactional public CourseDocument upload(UUID courseId, MultipartFile file) {
    courses.get(courseId); validatePdf(file); String originalName = Optional.ofNullable(file.getOriginalFilename()).orElse("document.pdf");
    CourseDocument document = documents.save(new CourseDocument(courseId, safeFilename(originalName), originalName, Optional.ofNullable(file.getContentType()).orElse("application/pdf")));
    document.processing(); documents.save(document);
    try {
      knowledge.ingestDocument(courseId, document.getId(), document.getFilename(), extractPages(file)); document.ready(); return documents.save(document);
    } catch (AiFeatureUnavailableException exception) { document.failed(exception.getMessage()); return documents.save(document); }
      catch (Exception exception) { document.failed(userMessage(exception)); return documents.save(document); }
  }
  @Transactional public void delete(UUID courseId, UUID documentId) { courses.get(courseId); CourseDocument document = documents.findById(documentId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document " + documentId + " was not found.")); if (!document.getCourseId().equals(courseId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document " + documentId + " was not found in this course."); knowledge.deleteDocumentVectors(documentId); documents.delete(document); }
  private List<LocalKnowledgeService.PageText> extractPages(MultipartFile file) throws IOException {
    try (PDDocument pdf = Loader.loadPDF(file.getBytes())) { PDFTextStripper stripper = new PDFTextStripper(); List<LocalKnowledgeService.PageText> pages = new ArrayList<>(); for (int page = 1; page <= pdf.getNumberOfPages(); page++) { stripper.setStartPage(page); stripper.setEndPage(page); String text = stripper.getText(pdf); if (text != null && !text.isBlank()) pages.add(new LocalKnowledgeService.PageText(page, text)); } return pages; }
  }
  private void validatePdf(MultipartFile file) { if (file == null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a non-empty PDF file."); String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT); if (!filename.endsWith(".pdf") || !"application/pdf".equalsIgnoreCase(file.getContentType())) throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only PDF files are supported."); }
  private String safeFilename(String filename) { return filename.replaceAll("[^a-zA-Z0-9._() -]", "_"); }
  private String userMessage(Exception exception) { if (exception instanceof IOException) return "PDF text extraction failed. Please upload a readable, non-encrypted PDF."; String message = exception.getMessage(); return message == null ? "Document processing failed." : message.length() > 480 ? message.substring(0, 480) : message; }
}
