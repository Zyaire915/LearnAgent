package com.learnagent.quiz;

import com.learnagent.memory.FlashcardService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/quizzes")
public class QuizController {
  private final QuizService service;
  private final FlashcardService flashcards;

  public QuizController(QuizService service, FlashcardService flashcards) {
    this.service = service;
    this.flashcards = flashcards;
  }

  @PostMapping("/generate")
  public ResponseEntity<QuizDtos.QuizView> generate(@Valid @RequestBody QuizDtos.GenerateRequest request) {
    Quiz quiz = service.generate(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(service.get(quiz.getId()));
  }

  @GetMapping
  public List<QuizDtos.QuizView> list(@RequestParam(required = false) UUID courseId) {
    return service.list(courseId);
  }

  @GetMapping("/history")
  public List<QuizDtos.HistoryView> history(@RequestParam(required = false) UUID courseId) {
    return service.history(courseId);
  }

  @GetMapping("/{id}/history")
  public QuizDtos.HistoryDetail historyDetail(@PathVariable UUID id) {
    return service.historyDetail(id);
  }

  @GetMapping("/{id}")
  public QuizDtos.QuizView get(@PathVariable UUID id) { return service.get(id); }

  @PostMapping("/{id}/submit")
  public QuizDtos.Submission submit(@PathVariable UUID id, @Valid @RequestBody QuizDtos.SubmitRequest request) {
    QuizDtos.Submission result = service.submit(id, request);
    flashcards.fromSubmission(id, result);
    return result;
  }
}
