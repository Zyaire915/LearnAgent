package com.learnagent.learning;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "learning_sessions")
public class LearningSession {
  @Id private UUID id;
  @Column(name = "course_id", nullable = false) private UUID courseId;
  @Column(nullable = false, length = 500) private String goal;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private LearningSessionStatus status;
  @Column(name = "current_round", nullable = false) private int currentRound;
  @Column(name = "mastery_threshold", nullable = false) private double masteryThreshold;
  @Column(name = "point_floor", nullable = false) private double pointFloor;
  @Column(name = "max_rounds", nullable = false) private int maxRounds;
  @Column(name = "web_enabled", nullable = false) private boolean webEnabled;
  @Column(name = "weak_points_json", nullable = false, columnDefinition = "text") private String weakPointsJson;
  @Column(name = "last_action") private String lastAction;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;
  @Column(name = "completed_at") private Instant completedAt;

  protected LearningSession() { }

  public LearningSession(UUID courseId, String goal, boolean webEnabled) {
    this.id = UUID.randomUUID();
    this.courseId = courseId;
    this.goal = goal;
    this.webEnabled = webEnabled;
    this.status = LearningSessionStatus.ACTIVE;
    this.masteryThreshold = 0.8;
    this.pointFloor = 0.6;
    this.maxRounds = 3;
    this.weakPointsJson = "[]";
  }

  @PrePersist void created() { createdAt = updatedAt = Instant.now(); }
  @PreUpdate void updated() { updatedAt = Instant.now(); }

  public void roundStarted(int number, LearningStrategy strategy) {
    currentRound = number;
    lastAction = strategy.name();
    status = LearningSessionStatus.AWAITING_SUBMISSION;
  }

  public void roundCompleted(boolean mastered, String weakPointsJson) {
    this.weakPointsJson = weakPointsJson;
    status = mastered ? LearningSessionStatus.MASTERED : LearningSessionStatus.AWAITING_CHOICE;
  }

  public void stop() {
    status = LearningSessionStatus.STOPPED;
    completedAt = Instant.now();
  }

  public UUID getId() { return id; }
  public UUID getCourseId() { return courseId; }
  public String getGoal() { return goal; }
  public LearningSessionStatus getStatus() { return status; }
  public int getCurrentRound() { return currentRound; }
  public double getMasteryThreshold() { return masteryThreshold; }
  public double getPointFloor() { return pointFloor; }
  public int getMaxRounds() { return maxRounds; }
  public boolean isWebEnabled() { return webEnabled; }
  public String getWeakPointsJson() { return weakPointsJson; }
  public String getLastAction() { return lastAction; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public Instant getCompletedAt() { return completedAt; }
}
