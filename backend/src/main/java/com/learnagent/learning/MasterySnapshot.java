package com.learnagent.learning;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mastery_snapshots")
public class MasterySnapshot {
  @Id private UUID id;
  @Column(name = "session_id", nullable = false) private UUID sessionId;
  @Column(name = "round_id", nullable = false) private UUID roundId;
  @Column(name = "knowledge_point", nullable = false) private String knowledgePoint;
  @Column(name = "attempt_count", nullable = false) private int attemptCount;
  @Column(name = "correct_count", nullable = false) private int correctCount;
  @Column(nullable = false) private double accuracy;
  @Column(name = "created_at", nullable = false) private Instant createdAt;

  protected MasterySnapshot() { }
  public MasterySnapshot(UUID sessionId, UUID roundId, String point, int attempts, int correct) {
    id = UUID.randomUUID(); this.sessionId = sessionId; this.roundId = roundId;
    knowledgePoint = point; attemptCount = attempts; correctCount = correct;
    accuracy = attempts == 0 ? 0 : (double) correct / attempts;
  }
  @PrePersist void created() { createdAt = Instant.now(); }
  public String getKnowledgePoint() { return knowledgePoint; }
  public int getAttemptCount() { return attemptCount; }
  public int getCorrectCount() { return correctCount; }
  public double getAccuracy() { return accuracy; }
}
