package com.learnagent.learning;

import com.learnagent.quiz.QuizDtos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LearningDtos {
  private LearningDtos() { }
  public record StartRequest(@NotNull UUID courseId, @NotBlank String goal, Boolean webEnabled) { }
  public record NextActionRequest(@NotNull LearningStrategy action) { }
  public record PointScore(int attempts, int correct, double accuracy) { }
  public record Recommendation(String action, String title, String description, boolean enabled) { }
  public record RoundView(UUID id, int roundNumber, String focus, LearningStrategy strategy,
                          Double overallAccuracy, boolean mastered, Map<String, PointScore> pointScores,
                          List<Recommendation> recommendations, Instant createdAt, Instant submittedAt,
                          QuizDtos.QuizView quiz) { }
  public record SessionView(UUID id, UUID courseId, String goal, LearningSessionStatus status,
                            int currentRound, int maxRounds, double masteryThreshold, double pointFloor,
                            boolean webEnabled, List<String> weakPoints, List<RoundView> rounds,
                            Instant createdAt, Instant updatedAt, Instant completedAt) { }
  public record SubmitView(QuizDtos.Submission submission, Map<String, PointScore> pointScores,
                           boolean mastered, boolean canContinue, int candidateFlashcards, List<Recommendation> recommendations,
                           SessionView session) { }
}
