package com.learnagent.course;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "courses")
public class Course {
  @Id private UUID id;
  @Column(nullable = false, length = 160) private String name;
  @Column(columnDefinition = "text") private String description;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;
  protected Course() { }
  public Course(String name, String description) { this.id = UUID.randomUUID(); this.name = name; this.description = description; }
  @PrePersist void onCreate() { Instant now = Instant.now(); if (id == null) id = UUID.randomUUID(); createdAt = now; updatedAt = now; }
  @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
  public UUID getId() { return id; }
  public String getName() { return name; }
  public String getDescription() { return description; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void update(String name, String description) { this.name = name; this.description = description; }
}
