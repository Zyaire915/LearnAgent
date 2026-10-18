package com.learnagent.notification;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "notifications")
public class Notification {
  @Id private UUID id;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private NotificationType type;
  @Column(nullable = false) private String title;
  @Column(nullable = false, columnDefinition = "text") private String body;
  @Column(name = "action_url") private String actionUrl;
  @Column(name = "reference_type") private String referenceType;
  @Column(name = "reference_id") private UUID referenceId;
  @Column(name = "read_at") private Instant readAt;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "dedup_key", nullable = false, unique = true) private String dedupKey;
  protected Notification() { }
  public Notification(NotificationType type, String title, String body, String actionUrl,
                      String referenceType, UUID referenceId, String dedupKey) {
    id = UUID.randomUUID(); this.type = type; this.title = title; this.body = body;
    this.actionUrl = actionUrl; this.referenceType = referenceType; this.referenceId = referenceId;
    this.dedupKey = dedupKey; createdAt = Instant.now();
  }
  public void markRead() { if (readAt == null) readAt = Instant.now(); }
  public UUID getId() { return id; } public NotificationType getType() { return type; }
  public String getTitle() { return title; } public String getBody() { return body; }
  public String getActionUrl() { return actionUrl; } public String getReferenceType() { return referenceType; }
  public UUID getReferenceId() { return referenceId; } public Instant getReadAt() { return readAt; }
  public Instant getCreatedAt() { return createdAt; } public boolean isRead() { return readAt != null; }
}
