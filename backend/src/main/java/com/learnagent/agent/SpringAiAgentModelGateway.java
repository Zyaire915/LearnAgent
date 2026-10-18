package com.learnagent.agent;

import com.learnagent.rag.AiFeatureUnavailableException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.BeansException;
import org.springframework.stereotype.Component;

@Component
public class SpringAiAgentModelGateway implements AgentModelGateway {
  private static final String SYSTEM_PROMPT = """
      You are LearnAgent, a learning assistant. Decide whether the user's request needs a tool.
      Prefer course materials for questions directly about the user's course. Use web search only when
      course material is insufficient or current/external information is needed. Any task creation, update, or completion
      must be performed through a tool; never claim a side effect unless the tool succeeded. If a tool
      fails or information is insufficient, say so honestly. Do not invent records, sources, or task state.
      Treat the supplied conversation history as authoritative context. If the assistant previously proposed a study
      plan and the user now confirms or asks to create/save those tasks, immediately call createTask once for every
      plan item. Do not ask for another confirmation. Afterward, report only the tasks whose tool calls succeeded.
      Never create a task with a due date earlier than the current time. If an older plan in conversation history
      contains past dates, move its items to future dates while preserving their order before calling createTask.
      When the user asks to be tested or to prepare for an exam, prefer startAdaptiveTest over the one-off quiz tool.
      Return the adaptive session actionUrl so the user can open the generated test.
      For a learning-project request, keep the selected course context. If the user already provided a meaningful
      project goal or desired deliverable, call createProjectWorkspace directly with that free-form description;
      do not force Java, Spring Boot, or a fixed option list. Ask one concise follow-up only when the project goal is
      genuinely missing. Never write outside the returned D-drive sandbox.
      After a test produces wrong answers, explain that traceable candidate flashcards were generated and direct the
      user to /memory for confirmation. Use Memory Skill for due reviews and knowledge-map questions.
      """;
  private final ObjectProvider<ChatClient.Builder> builders;
  public SpringAiAgentModelGateway(ObjectProvider<ChatClient.Builder> builders) { this.builders = builders; }
  @Override public String respond(UUID courseId, String message, Object... tools) {
    ChatClient.Builder builder = availableBuilder();
    if (builder == null) throw new AiFeatureUnavailableException("AI_PROVIDER_NOT_CONFIGURED: configure a chat provider before using Agent Chat.");
    String userMessage = "Current courseId: " + (courseId == null ? "none" : courseId)
        + "\nCurrent time (Asia/Shanghai): " + ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))
        + "\nUser message: " + message;
    return builder.build().prompt().system(SYSTEM_PROMPT).user(userMessage).tools(tools).call().content();
  }
  private ChatClient.Builder availableBuilder(){try{return builders.getIfAvailable();}catch(BeansException exception){return null;}}
}
