package com.learnagent.quiz;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public final class QuizDtos {
  private QuizDtos() { }
  public record GenerateRequest(@NotNull UUID courseId, String knowledgePoint, @Min(3) @Max(10) Integer questionCount) { }
  public record SourceRef(String type, String title, String url, String filename, Integer pageNumber) { }
  public record QuestionView(UUID id, String content, List<String> options, String knowledgePoint, List<SourceRef> sources) { }
  public record QuizView(UUID id, UUID courseId, String title, String knowledgePoint, QuizStatus status,
                         UUID learningSessionId, Integer learningRoundNumber, List<QuestionView> questions) { }
  public record SubmitRequest(@NotEmpty List<Answer> answers) { }
  public record Answer(@NotNull UUID questionId, String answer) { }
  public record Result(UUID questionId, boolean correct, String correctAnswer, String explanation, String knowledgePoint) { }
  public record Submission(int totalQuestions, int correctCount, double accuracy, List<Result> results) { }
  public record GeneratedQuiz(String title, List<GeneratedQuestion> questions) { }
  public record GeneratedQuestion(String content, List<String> options, String correctAnswer, String explanation, String knowledgePoint) { }
}
