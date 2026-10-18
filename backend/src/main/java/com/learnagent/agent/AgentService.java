package com.learnagent.agent;

import com.learnagent.skill.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class AgentService {
  private final AgentModelGateway model; private final AgentExecutionTracker tracker;
  private final KnowledgeSkill knowledge; private final WebSearchSkill web; private final TaskSkill tasks; private final QuizSkill quizzes; private final AnalysisSkill analysis; private final PlanningSkill planning;private final WeeklyReviewSkill weekly;private final AdaptiveLearningSkill adaptive;private final WorkspaceSkill workspace;private final MemorySkill memory;
  public AgentService(AgentModelGateway model, AgentExecutionTracker tracker, KnowledgeSkill knowledge, WebSearchSkill web, TaskSkill tasks, QuizSkill quizzes, AnalysisSkill analysis, PlanningSkill planning,WeeklyReviewSkill weekly,AdaptiveLearningSkill adaptive,WorkspaceSkill workspace,MemorySkill memory) { this.model=model;this.tracker=tracker;this.knowledge=knowledge;this.web=web;this.tasks=tasks;this.quizzes=quizzes;this.analysis=analysis;this.planning=planning;this.weekly=weekly;this.adaptive=adaptive;this.workspace=workspace;this.memory=memory; }
  public AgentResponse chat(UUID courseId, String message) {
    return chat(courseId, message, List.of());
  }
  public AgentResponse chat(UUID courseId, String message, List<AgentConversationMessage> history) {
    if (message == null || message.isBlank()) throw new IllegalArgumentException("Message is required.");
    tracker.begin();
    String answer;
    try { answer = model.respond(courseId, contextualMessage(history, message), knowledge, web, tasks, quizzes, analysis, planning,weekly,adaptive,workspace,memory); }
    catch (RuntimeException exception) { AgentExecutionTracker.Snapshot snapshot = tracker.finish(); return new AgentResponse("Agent request failed: " + rootMessage(exception), snapshot.steps(), snapshot.sources()); }
    AgentExecutionTracker.Snapshot snapshot = tracker.finish();
    return new AgentResponse(answer, snapshot.steps(), snapshot.sources());
  }
  String contextualMessage(List<AgentConversationMessage> history, String currentMessage) {
    if (history == null || history.isEmpty()) return currentMessage.trim();
    List<AgentConversationMessage> safe = history.stream()
        .filter(Objects::nonNull)
        .filter(item -> item.content() != null && !item.content().isBlank())
        .filter(item -> "user".equalsIgnoreCase(item.role()) || "assistant".equalsIgnoreCase(item.role()))
        .toList();
    int start = Math.max(0, safe.size() - 8);
    StringBuilder context = new StringBuilder("Conversation history (chronological):\n");
    for (int index = start; index < safe.size(); index++) {
      AgentConversationMessage item = safe.get(index);
      String content = item.content().trim();
      if (content.length() > 4000) content = content.substring(0, 4000);
      context.append(item.role().toUpperCase(Locale.ROOT)).append(": ").append(content).append('\n');
    }
    return context.append("\nCurrent user request:\n").append(currentMessage.trim()).toString();
  }
  private String rootMessage(Throwable error) { Throwable current=error;while(current.getCause()!=null)current=current.getCause();return current.getMessage()==null?current.getClass().getSimpleName():current.getMessage(); }
}
