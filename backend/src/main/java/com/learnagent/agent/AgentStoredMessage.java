package com.learnagent.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_conversation_messages")
public class AgentStoredMessage {
  @Id private UUID id;
  @Column(name = "conversation_id", nullable = false) private UUID conversationId;
  @Column(nullable = false, length = 16) private String role;
  @Column(nullable = false, columnDefinition = "text") private String content;
  @Column(name = "sources_json", nullable = false, columnDefinition = "text") private String sourcesJson;
  @Column(name = "steps_json", nullable = false, columnDefinition = "text") private String stepsJson;
  @Column(name = "created_at", nullable = false) private Instant createdAt;

  protected AgentStoredMessage() { }

  public AgentStoredMessage(UUID conversationId, String role, String content, String sourcesJson, String stepsJson) {
    this.id = UUID.randomUUID();
    this.conversationId = conversationId;
    this.role = role;
    this.content = content;
    this.sourcesJson = sourcesJson == null ? "[]" : sourcesJson;
    this.stepsJson = stepsJson == null ? "[]" : stepsJson;
    this.createdAt = Instant.now();
  }

  public UUID getId() { return id; }
  public UUID getConversationId() { return conversationId; }
  public String getRole() { return role; }
  public String getContent() { return content; }
  public String getSourcesJson() { return sourcesJson; }
  public String getStepsJson() { return stepsJson; }
  public Instant getCreatedAt() { return createdAt; }
}
