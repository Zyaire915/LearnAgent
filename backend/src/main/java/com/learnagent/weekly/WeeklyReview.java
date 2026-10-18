package com.learnagent.weekly;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "weekly_reviews")
public class WeeklyReview {
  @Id private UUID id;
  @Column(name = "period_start", nullable = false) private Instant periodStart;
  @Column(name = "period_end", nullable = false) private Instant periodEnd;
  @Column(name = "generated_at", nullable = false) private Instant generatedAt;
  @Column(columnDefinition = "text") private String summary;
  @Column(columnDefinition = "text") private String recommendation;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private WeeklyReviewStatus status;
  @Column(name = "statistics_json", nullable = false, columnDefinition = "text") private String statisticsJson;
  @Column(name = "recommended_knowledge_points_json", nullable = false, columnDefinition = "text") private String recommendedKnowledgePointsJson;
  @Column(name = "details_json", nullable = false, columnDefinition = "text") private String detailsJson;
  @Column(name = "memory_statistics_json", nullable = false, columnDefinition = "text") private String memoryStatisticsJson;

  protected WeeklyReview() { }

  public WeeklyReview(Instant start, Instant end, Instant generated, String summary, String recommendation,
                      WeeklyReviewStatus status, String statistics, String recommended, String details,
                      String memoryStatistics) {
    id = UUID.randomUUID(); periodStart = start; periodEnd = end; generatedAt = generated;
    this.summary = summary; this.recommendation = recommendation; this.status = status;
    statisticsJson = statistics; recommendedKnowledgePointsJson = recommended; detailsJson = details;
    memoryStatisticsJson = memoryStatistics == null ? "{}" : memoryStatistics;
  }

  public UUID getId(){return id;} public Instant getPeriodStart(){return periodStart;}
  public Instant getPeriodEnd(){return periodEnd;} public Instant getGeneratedAt(){return generatedAt;}
  public String getSummary(){return summary;} public String getRecommendation(){return recommendation;}
  public WeeklyReviewStatus getStatus(){return status;} public String getStatisticsJson(){return statisticsJson;}
  public String getRecommendedKnowledgePointsJson(){return recommendedKnowledgePointsJson;}
  public String getDetailsJson(){return detailsJson;} public String getMemoryStatisticsJson(){return memoryStatisticsJson;}
}
