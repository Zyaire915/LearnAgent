package com.learnagent.controller;

import com.learnagent.service.AiTestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiTestController {
  private final AiTestService aiTestService;
  public AiTestController(AiTestService aiTestService) { this.aiTestService = aiTestService; }
  @GetMapping("/test")
  public ResponseEntity<AiTestService.AiTestResult> test() {
    var result = aiTestService.test();
    return ResponseEntity.status(result.success() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(result);
  }
}
