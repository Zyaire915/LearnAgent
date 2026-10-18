package com.learnagent.controller;

import com.learnagent.service.SystemStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemController {
  private final SystemStatusService systemStatusService;
  public SystemController(SystemStatusService systemStatusService) { this.systemStatusService = systemStatusService; }
  @GetMapping("/status")
  public Map<String, Object> status() {
    var status = systemStatusService.status();
    return Map.of("service", "LearnAgent", "databaseConnected", status.databaseConnected(),
        "llmConfigured", status.llmConfigured(), "javaVersion", System.getProperty("java.version"),
        "chatProvider",status.chatProvider(),"embeddingProvider",status.embeddingProvider(),"vectorStore",status.vectorStore(),"webSearch",status.webSearch(),"scheduler",status.scheduler());
  }
}
