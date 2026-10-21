package com.learnagent.agent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentConversationRepository extends JpaRepository<AgentConversation, UUID> {
  List<AgentConversation> findByCourseIdOrderByUpdatedAtDesc(UUID courseId);
  Optional<AgentConversation> findByIdAndCourseId(UUID id, UUID courseId);
}
