package com.learnagent.agent;

import com.learnagent.knowledge.KnowledgeSource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AgentConversationDtos {
  private AgentConversationDtos() { }

  public record Summary(UUID id, UUID courseId, String title, String preview,
                        long messageCount, Instant updatedAt) { }
  public record MessageView(UUID id, String role, String text, List<KnowledgeSource> sources,
                            List<AgentStep> steps, Instant createdAt) { }
  public record Detail(UUID id, UUID courseId, String title, Instant createdAt,
                       Instant updatedAt, List<MessageView> messages) { }
  public record Turn(UUID conversationId, String answer, List<KnowledgeSource> sources,
                     List<AgentStep> steps) { }
}
