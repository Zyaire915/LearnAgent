package com.learnagent.memory;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "flashcards")
public class Flashcard {
  @Id private UUID id;
  @Column(name="course_id",nullable=false) private UUID courseId;
  @Column(name="source_quiz_id") private UUID sourceQuizId;
  @Column(name="source_question_id") private UUID sourceQuestionId;
  @Column(name="source_document_id") private UUID sourceDocumentId;
  @Enumerated(EnumType.STRING) @Column(name="card_type",nullable=false) private FlashcardType cardType;
  @Column(nullable=false,columnDefinition="text") private String front;
  @Column(nullable=false,columnDefinition="text") private String back;
  @Column(name="knowledge_point",nullable=false) private String knowledgePoint;
  @Enumerated(EnumType.STRING) @Column(nullable=false) private FlashcardStatus status;
  @Column(name="source_json",nullable=false,columnDefinition="text") private String sourceJson;
  @Column(name="content_hash",nullable=false) private String contentHash;
  @Column(name="created_at",nullable=false) private Instant createdAt;
  @Column(name="updated_at",nullable=false) private Instant updatedAt;
  @Column(name="approved_at") private Instant approvedAt;
  @Column(name="suspended_at") private Instant suspendedAt;

  protected Flashcard() { }
  public Flashcard(UUID courseId, UUID quizId, UUID questionId, UUID documentId, FlashcardType type,
                   String front, String back, String point, String sourceJson, String hash) {
    id=UUID.randomUUID();this.courseId=courseId;sourceQuizId=quizId;sourceQuestionId=questionId;
    sourceDocumentId=documentId;cardType=type;this.front=front;this.back=back;knowledgePoint=point;
    status=FlashcardStatus.CANDIDATE;
    this.sourceJson = quizId != null && (sourceJson == null || sourceJson.isBlank() || "[]".equals(sourceJson))
      ? "[{\"type\":\"QUIZ\",\"title\":\"测验错题记录\",\"filename\":\"测验错题\"}]"
      : sourceJson;
    contentHash=hash;
  }
  @PrePersist void created(){createdAt=updatedAt=Instant.now();}
  @PreUpdate void updated(){updatedAt=Instant.now();}
  public void edit(String front,String back,String point,FlashcardType type){this.front=front;this.back=back;knowledgePoint=point;cardType=type;}
  public void approve(){status=FlashcardStatus.ACTIVE;approvedAt=Instant.now();suspendedAt=null;}
  public void suspend(){status=FlashcardStatus.SUSPENDED;suspendedAt=Instant.now();}
  public void reject(){status=FlashcardStatus.REJECTED;suspendedAt=Instant.now();}
  public UUID getId(){return id;}public UUID getCourseId(){return courseId;}public UUID getSourceQuizId(){return sourceQuizId;}
  public UUID getSourceQuestionId(){return sourceQuestionId;}public UUID getSourceDocumentId(){return sourceDocumentId;}
  public FlashcardType getCardType(){return cardType;}public String getFront(){return front;}public String getBack(){return back;}
  public String getKnowledgePoint(){return knowledgePoint;}public FlashcardStatus getStatus(){return status;}
  public String getSourceJson(){
    if(sourceQuestionId!=null&&(sourceJson==null||sourceJson.isBlank()||"[]".equals(sourceJson)))
      return "[{\"type\":\"QUIZ\",\"title\":\"测验错题记录\",\"filename\":\"测验错题\"}]";
    return sourceJson;
  }public Instant getCreatedAt(){return createdAt;}
  public Instant getUpdatedAt(){return updatedAt;}public Instant getApprovedAt(){return approvedAt;}
}
