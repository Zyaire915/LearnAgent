package com.learnagent.quiz;

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
@Table(name = "quizzes")
public class Quiz {
  @Id private UUID id;
  @Column(name = "course_id", nullable = false) private UUID courseId;
  @Column(nullable = false) private String title;
  @Column(name = "knowledge_point") private String knowledgePoint;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private QuizStatus status;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "completed_at") private Instant completedAt;
  @Column(name = "learning_session_id") private UUID learningSessionId;
  @Column(name = "learning_round_number") private Integer learningRoundNumber;
  @Column(name = "source_snapshot_json", columnDefinition = "text") private String sourceSnapshotJson;

  protected Quiz() { }
  public Quiz(UUID courseId, String title, String knowledgePoint) {
    id = UUID.randomUUID(); this.courseId = courseId; this.title = title;
    this.knowledgePoint = knowledgePoint; status = QuizStatus.GENERATED;
  }
  @PrePersist void created() { createdAt = Instant.now(); }
  public void complete() { status = QuizStatus.COMPLETED; completedAt = Instant.now(); }
  public void attachLearningContext(UUID sessionId, Integer roundNumber, String sourcesJson) {
    learningSessionId = sessionId; learningRoundNumber = roundNumber; sourceSnapshotJson = sourcesJson;
  }
  public UUID getId() { return id; }
  public UUID getCourseId() { return courseId; }
  public String getTitle() { return title; }
  public String getKnowledgePoint() { return knowledgePoint; }
  public QuizStatus getStatus() { return status; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getCompletedAt() { return completedAt; }
  public UUID getLearningSessionId() { return learningSessionId; }
  public Integer getLearningRoundNumber() { return learningRoundNumber; }
  public String getSourceSnapshotJson() { return sourceSnapshotJson; }
}
