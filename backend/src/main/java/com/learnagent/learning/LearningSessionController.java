package com.learnagent.learning;

import com.learnagent.quiz.QuizDtos;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learning-sessions")
public class LearningSessionController {
  private final LearningSessionService service;
  public LearningSessionController(LearningSessionService service) { this.service = service; }

  @PostMapping public ResponseEntity<LearningDtos.SessionView> start(@Valid @RequestBody LearningDtos.StartRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.start(request));
  }
  @GetMapping public List<LearningDtos.SessionView> list(@RequestParam(required = false) UUID courseId) {
    return service.list(courseId);
  }
  @GetMapping("/{id}") public LearningDtos.SessionView get(@PathVariable UUID id) { return service.get(id); }
  @PostMapping("/{id}/rounds") public LearningDtos.RoundView round(
      @PathVariable UUID id, @Valid @RequestBody LearningDtos.NextActionRequest request) {
    return service.createRound(id, request.action());
  }
  @PostMapping("/{id}/rounds/{roundId}/submit") public LearningDtos.SubmitView submit(
      @PathVariable UUID id, @PathVariable UUID roundId, @Valid @RequestBody QuizDtos.SubmitRequest request) {
    return service.submit(id, roundId, request);
  }
  @PostMapping("/{id}/next-action") public LearningDtos.SessionView next(
      @PathVariable UUID id, @Valid @RequestBody LearningDtos.NextActionRequest request) {
    return service.nextAction(id, request.action());
  }
  @PostMapping("/{id}/stop") public LearningDtos.SessionView stop(@PathVariable UUID id) { return service.stop(id); }
}
