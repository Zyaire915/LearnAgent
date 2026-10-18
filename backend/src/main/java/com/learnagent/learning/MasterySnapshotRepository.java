package com.learnagent.learning;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MasterySnapshotRepository extends JpaRepository<MasterySnapshot, UUID> {
  List<MasterySnapshot> findBySessionId(UUID sessionId);
}
