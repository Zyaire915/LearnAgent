package com.learnagent.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_conversations")
public class AgentConversation {
  @Id private UUID id;
  @Column(name = "course_id", nullable = false) private UUID courseId;
  @Column(nullable = false, length = 200) private String title;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;

  protected AgentConversation() { }

  public AgentConversation(UUID courseId, String title) {
    this.id = UUID.randomUUID();
    this.courseId = courseId;
    this.title = title;
  }

  @PrePersist void onCreate() {
    Instant now = Instant.now();
    if (id == null) id = UUID.randomUUID();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate void onUpdate() { updatedAt = Instant.now(); }

  public UUID getId() { return id; }
  public UUID getCourseId() { return courseId; }
  public String getTitle() { return title; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void touch() { updatedAt = Instant.now(); }
}
