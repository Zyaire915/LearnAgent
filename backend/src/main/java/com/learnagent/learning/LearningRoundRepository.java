package com.learnagent.learning;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningRoundRepository extends JpaRepository<LearningRound, UUID> {
  List<LearningRound> findBySessionIdOrderByRoundNumber(UUID sessionId);
  Optional<LearningRound> findByIdAndSessionId(UUID id, UUID sessionId);
}
