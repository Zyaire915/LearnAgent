package com.learnagent.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class AgentController {
  private final AgentService service;

  public AgentController(AgentService service) {
    this.service = service;
  }

  @PostMapping("/chat")
  public AgentResponse chat(@Valid @RequestBody AgentRequest request) {
    return service.chat(request.courseId(), request.message(), request.history());
  }

  public record AgentRequest(
      UUID courseId,
      @NotBlank String message,
      List<AgentConversationMessage> history) { }
}
