package com.learnagent.agent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentStoredMessageRepository extends JpaRepository<AgentStoredMessage, UUID> {
  List<AgentStoredMessage> findByConversationIdOrderByCreatedAtAscIdAsc(UUID conversationId);
  List<AgentStoredMessage> findTop8ByConversationIdOrderByCreatedAtDescIdDesc(UUID conversationId);
  Optional<AgentStoredMessage> findTopByConversationIdOrderByCreatedAtDescIdDesc(UUID conversationId);
  long countByConversationId(UUID conversationId);
}
