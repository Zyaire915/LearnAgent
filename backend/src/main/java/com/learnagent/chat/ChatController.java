package com.learnagent.chat;

import com.learnagent.chat.ChatDtos.*;
import com.learnagent.course.CourseService;
import com.learnagent.rag.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/chat")
public class ChatController {
  private final CourseService courses; private final LocalKnowledgeService knowledge;
  public ChatController(CourseService courses, LocalKnowledgeService knowledge) { this.courses = courses; this.knowledge = knowledge; }
  @PostMapping public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
    courses.get(request.courseId()); RagAnswer answer = knowledge.answer(request.courseId(), request.message());
    return new ChatResponse(answer.answer(), answer.sources().stream().map(source -> new SourceResponse(source.documentId(), source.filename(), source.pageNumber(), source.snippet(), source.score())).toList());
  }
}
