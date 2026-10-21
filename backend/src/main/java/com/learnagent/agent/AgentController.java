package com.learnagent.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class AgentController {
  private final AgentConversationService conversations;

  public AgentController(AgentConversationService conversations) {
    this.conversations = conversations;
  }

  @PostMapping("/chat")
  public AgentConversationDtos.Turn chat(@Valid @RequestBody AgentRequest request) {
    return conversations.chat(request.courseId(), request.conversationId(), request.message());
  }

  @GetMapping("/conversations")
  public List<AgentConversationDtos.Summary> conversations(@RequestParam UUID courseId) {
    return conversations.list(courseId);
  }

  @GetMapping("/conversations/{id}")
  public AgentConversationDtos.Detail conversation(@PathVariable UUID id) { return conversations.get(id); }

  @DeleteMapping("/conversations/{id}")
  public void deleteConversation(@PathVariable UUID id) { conversations.delete(id); }

  public record AgentRequest(
      UUID courseId,
      @NotBlank String message,
      List<AgentConversationMessage> history,
      UUID conversationId) { }
}
