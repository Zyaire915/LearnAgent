package com.learnagent.digest;
import jakarta.persistence.*;import java.time.*;import java.util.UUID;
@Entity@Table(name="external_resources")public class ExternalResource{@Id private UUID id;@Column(name="digest_id")private UUID digestId;@Column(name="course_id")private UUID courseId;@Column(nullable=false)private String title;@Column(nullable=false,length=2000)private String url;@Column(name="source_name")private String sourceName;@Column(nullable=false,columnDefinition="text")private String summary;@Column(name="published_at")private Instant publishedAt;@Column(name="discovered_at",nullable=false)private Instant discoveredAt;@Column(name="local_markdown_path")private String localMarkdownPath;@Column(name="review_score") private Integer reviewScore;
@Column(name="review_tier") private String reviewTier;
@Column(name="review_status",nullable=false) private String reviewStatus="UNREVIEWED";
@Column(name="review_reason",columnDefinition="text") private String reviewReason;
public ExternalResource reviewed(com.learnagent.websearch.ResourceReview review){if(review!=null){reviewScore=review.score();reviewTier=review.tier();reviewStatus=review.status();reviewReason=review.reason();}return this;}
public Integer getReviewScore(){return reviewScore;}
public String getReviewTier(){return reviewTier;}
public String getReviewStatus(){return reviewStatus;}
public String getReviewReason(){return reviewReason;}
protected ExternalResource(){}public ExternalResource(UUID digest,UUID course,String title,String url,String source,String summary,Instant published,String path){id=UUID.randomUUID();digestId=digest;courseId=course;this.title=title;this.url=url;sourceName=source;this.summary=summary;publishedAt=published;localMarkdownPath=path;discoveredAt=Instant.now();}public UUID getId(){return id;}public UUID getDigestId(){return digestId;}public UUID getCourseId(){return courseId;}public String getTitle(){return title;}public String getUrl(){return url;}public String getSourceName(){return sourceName;}public String getSummary(){return summary;}public Instant getPublishedAt(){return publishedAt;}public Instant getDiscoveredAt(){return discoveredAt;}public String getLocalMarkdownPath(){return localMarkdownPath;}}
