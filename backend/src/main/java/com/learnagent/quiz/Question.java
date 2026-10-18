package com.learnagent.quiz;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "questions")
public class Question {
  @Id private UUID id;
  @Column(name = "quiz_id", nullable = false) private UUID quizId;
  @Column(nullable = false, columnDefinition = "text") private String content;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private QuestionType type;
  @Column(name = "options_json", nullable = false, columnDefinition = "text") private String optionsJson;
  @Column(name = "correct_answer", nullable = false) private String correctAnswer;
  @Column(columnDefinition = "text") private String explanation;
  @Column(name = "knowledge_point", nullable = false) private String knowledgePoint;
  @Column(name = "source_json", columnDefinition = "text") private String sourceJson;

  protected Question() { }
  public Question(UUID quizId, String content, String options, String correct, String explanation, String point) {
    this(quizId, content, options, correct, explanation, point, null);
  }
  public Question(UUID quizId, String content, String options, String correct, String explanation, String point, String sources) {
    id = UUID.randomUUID(); this.quizId = quizId; this.content = content;
    type = QuestionType.SINGLE_CHOICE; optionsJson = options; correctAnswer = correct;
    this.explanation = explanation; knowledgePoint = point; sourceJson = sources;
  }
  public UUID getId() { return id; }
  public UUID getQuizId() { return quizId; }
  public String getContent() { return content; }
  public String getOptionsJson() { return optionsJson; }
  public String getCorrectAnswer() { return correctAnswer; }
  public String getExplanation() { return explanation; }
  public String getKnowledgePoint() { return knowledgePoint; }
  public String getSourceJson() { return sourceJson; }
}
