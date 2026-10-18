package com.learnagent.agent;

import com.learnagent.knowledge.KnowledgeSource;
import java.util.*;
import java.util.function.*;
import org.springframework.stereotype.Component;

@Component
public class AgentExecutionTracker {
  private final ThreadLocal<Session> current = new ThreadLocal<>();
  public void begin() { current.set(new Session()); }
  public Snapshot finish() { Session session = current.get(); current.remove(); return session == null ? new Snapshot(List.of(), List.of()) : session.snapshot(); }
  public <T> T execute(String skill, String tool, Supplier<T> action, Function<T, String> summary) {
    Session session = current.get();
    if (session == null) return action.get();
    session.steps.add(new AgentStep(skill, tool, AgentStepStatus.STARTED, "Started"));
    try {
      T result = action.get();
      session.steps.add(new AgentStep(skill, tool, AgentStepStatus.SUCCESS, safe(summary.apply(result))));
      return result;
    } catch (RuntimeException exception) {
      session.steps.add(new AgentStep(skill, tool, AgentStepStatus.FAILED, safe(rootMessage(exception))));
      throw exception;
    }
  }
  public void addSources(Collection<KnowledgeSource> sources) { Session session = current.get(); if (session != null) session.sources.addAll(sources); }
  private String safe(String value) { if (value == null || value.isBlank()) return "Completed"; return value.length() > 240 ? value.substring(0, 237) + "..." : value; }
  private String rootMessage(Throwable error) { Throwable current = error; while (current.getCause() != null) current = current.getCause(); return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage(); }
  private static final class Session { private final List<AgentStep> steps = new ArrayList<>(); private final List<KnowledgeSource> sources = new ArrayList<>(); private Snapshot snapshot() { return new Snapshot(List.copyOf(steps), deduplicate(sources)); } private List<KnowledgeSource> deduplicate(List<KnowledgeSource> values) { Map<String, KnowledgeSource> unique = new LinkedHashMap<>(); values.forEach(source -> unique.putIfAbsent(source.type() + ":" + source.documentId() + ":" + source.pageNumber() + ":" + source.url(), source)); return List.copyOf(unique.values()); } }
  public record Snapshot(List<AgentStep> steps, List<KnowledgeSource> sources) { }
}
