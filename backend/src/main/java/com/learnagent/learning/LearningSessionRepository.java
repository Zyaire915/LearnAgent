package com.learnagent.learning;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningSessionRepository extends JpaRepository<LearningSession, UUID> {
  List<LearningSession> findByCourseIdOrderByUpdatedAtDesc(UUID courseId);
  List<LearningSession> findAllByOrderByUpdatedAtDesc();
}
