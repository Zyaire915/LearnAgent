package com.learnagent.learning;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "learning_rounds")
public class LearningRound {
  @Id private UUID id;
  @Column(name = "session_id", nullable = false) private UUID sessionId;
  @Column(name = "quiz_id", nullable = false) private UUID quizId;
  @Column(name = "round_number", nullable = false) private int roundNumber;
  private String focus;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private LearningStrategy strategy;
  @Column(name = "overall_accuracy") private Double overallAccuracy;
  @Column(nullable = false) private boolean mastered;
  @Column(name = "point_scores_json", nullable = false, columnDefinition = "text") private String pointScoresJson;
  @Column(name = "recommendations_json", nullable = false, columnDefinition = "text") private String recommendationsJson;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "submitted_at") private Instant submittedAt;

  protected LearningRound() { }

  public LearningRound(UUID sessionId, UUID quizId, int roundNumber, String focus, LearningStrategy strategy) {
    id = UUID.randomUUID();
    this.sessionId = sessionId;
    this.quizId = quizId;
    this.roundNumber = roundNumber;
    this.focus = focus;
    this.strategy = strategy;
    pointScoresJson = "{}";
    recommendationsJson = "[]";
  }

  @PrePersist void created() { createdAt = Instant.now(); }

  public void complete(double accuracy, boolean mastered, String scores, String recommendations) {
    overallAccuracy = accuracy;
    this.mastered = mastered;
    pointScoresJson = scores;
    recommendationsJson = recommendations;
    submittedAt = Instant.now();
  }

  public UUID getId() { return id; }
  public UUID getSessionId() { return sessionId; }
  public UUID getQuizId() { return quizId; }
  public int getRoundNumber() { return roundNumber; }
  public String getFocus() { return focus; }
  public LearningStrategy getStrategy() { return strategy; }
  public Double getOverallAccuracy() { return overallAccuracy; }
  public boolean isMastered() { return mastered; }
  public String getPointScoresJson() { return pointScoresJson; }
  public String getRecommendationsJson() { return recommendationsJson; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getSubmittedAt() { return submittedAt; }
}
