package com.learnagent.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnagent.agent.AgentConversationDtos.Detail;
import com.learnagent.agent.AgentConversationDtos.MessageView;
import com.learnagent.agent.AgentConversationDtos.Summary;
import com.learnagent.agent.AgentConversationDtos.Turn;
import com.learnagent.course.CourseService;
import com.learnagent.knowledge.KnowledgeSource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgentConversationService {
  private static final TypeReference<List<KnowledgeSource>> SOURCES_TYPE = new TypeReference<>() { };
  private static final TypeReference<List<AgentStep>> STEPS_TYPE = new TypeReference<>() { };

  private final AgentConversationRepository conversations;
  private final AgentStoredMessageRepository messages;
  private final AgentService agent;
  private final CourseService courses;
  private final ObjectMapper json;

  public AgentConversationService(AgentConversationRepository conversations,
      AgentStoredMessageRepository messages, AgentService agent, CourseService courses, ObjectMapper json) {
    this.conversations = conversations;
    this.messages = messages;
    this.agent = agent;
    this.courses = courses;
    this.json = json;
  }

  public Turn chat(UUID courseId, UUID conversationId, String message) {
    courses.get(courseId);
    AgentConversation conversation;
    List<AgentConversationMessage> history;
    if (conversationId == null) {
      conversation = conversations.save(new AgentConversation(courseId, titleFrom(message)));
      history = List.of();
    } else {
      conversation = conversations.findByIdAndCourseId(conversationId, courseId)
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found."));
      history = recentHistory(conversationId);
    }

    messages.save(new AgentStoredMessage(conversation.getId(), "user", message.trim(), "[]", "[]"));
    AgentResponse response = agent.chat(courseId, message, history);
    messages.save(new AgentStoredMessage(conversation.getId(), "assistant", response.answer(),
        write(response.sources()), write(response.steps())));
    conversation.touch();
    conversations.save(conversation);
    return new Turn(conversation.getId(), response.answer(), response.sources(), response.steps());
  }

  public List<Summary> list(UUID courseId) {
    courses.get(courseId);
    return conversations.findByCourseIdOrderByUpdatedAtDesc(courseId).stream().map(conversation -> {
      AgentStoredMessage latest = messages.findTopByConversationIdOrderByCreatedAtDescIdDesc(conversation.getId()).orElse(null);
      return new Summary(conversation.getId(), conversation.getCourseId(), conversation.getTitle(),
          latest == null ? "" : preview(latest.getContent()), messages.countByConversationId(conversation.getId()),
          conversation.getUpdatedAt());
    }).toList();
  }

  public Detail get(UUID id) {
    AgentConversation conversation = conversations.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found."));
    List<MessageView> entries = messages.findByConversationIdOrderByCreatedAtAscIdAsc(id).stream()
        .map(item -> new MessageView(item.getId(), item.getRole(), item.getContent(),
            read(item.getSourcesJson(), SOURCES_TYPE), read(item.getStepsJson(), STEPS_TYPE), item.getCreatedAt()))
        .toList();
    return new Detail(conversation.getId(), conversation.getCourseId(), conversation.getTitle(),
        conversation.getCreatedAt(), conversation.getUpdatedAt(), entries);
  }

  public void delete(UUID id) {
    if (!conversations.existsById(id)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found.");
    }
    conversations.deleteById(id);
  }

  private List<AgentConversationMessage> recentHistory(UUID conversationId) {
    List<AgentStoredMessage> recent = messages.findTop8ByConversationIdOrderByCreatedAtDescIdDesc(conversationId);
    List<AgentConversationMessage> history = new ArrayList<>();
    for (int index = recent.size() - 1; index >= 0; index--) {
      AgentStoredMessage item = recent.get(index);
      history.add(new AgentConversationMessage(item.getRole(), item.getContent()));
    }
    return history;
  }

  private String titleFrom(String message) {
    String title = message == null ? "新对话" : message.trim().replaceAll("\\s+", " ");
    if (title.isBlank()) return "新对话";
    return title.length() <= 200 ? title : title.substring(0, 197) + "…";
  }

  private String preview(String content) {
    String text = content == null ? "" : content.replaceAll("\\s+", " ").trim();
    return text.length() <= 100 ? text : text.substring(0, 97) + "…";
  }

  private String write(Object value) {
    try { return json.writeValueAsString(value); }
    catch (Exception exception) { return "[]"; }
  }

  private <T> List<T> read(String value, TypeReference<List<T>> type) {
    try { return json.readValue(value, type); }
    catch (Exception exception) { return List.of(); }
  }
}
