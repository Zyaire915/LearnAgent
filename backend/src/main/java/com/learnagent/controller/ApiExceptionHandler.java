package com.learnagent.controller;

import com.learnagent.rag.AiFeatureUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException exception) {
    String message = exception.getReason() == null ? "The request could not be completed." : exception.getReason();
    return ResponseEntity.status(exception.getStatusCode()).body(Map.of("message", message, "timestamp", Instant.now().toString()));
  }
  @ExceptionHandler(AiFeatureUnavailableException.class)
  public ResponseEntity<Map<String, Object>> handleAiUnavailable(AiFeatureUnavailableException exception) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", exception.getMessage(), "timestamp", Instant.now().toString()));
  }
  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handle(Exception exception) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
        "message", "The server could not complete this request.", "timestamp", Instant.now().toString()));
  }
}
