package com.learnagent.skill;

import com.learnagent.agent.AgentExecutionTracker;
import com.learnagent.learning.LearningDtos;
import com.learnagent.learning.LearningSessionService;
import com.learnagent.learning.LearningStrategy;
import java.util.UUID;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class AdaptiveLearningSkill {
  private final LearningSessionService service;
  private final AgentExecutionTracker tracker;
  public AdaptiveLearningSkill(LearningSessionService service, AgentExecutionTracker tracker) {
    this.service = service; this.tracker = tracker;
  }

  @Tool(description = "Start a persistent five-question adaptive test using course PDFs and optional web sources. Use when the user asks to test, quiz, assess, or prepare for an exam.")
  public SessionResult startAdaptiveTest(
      @ToolParam(description = "Course ID") UUID courseId,
      @ToolParam(description = "Learning or exam goal") String goal,
      @ToolParam(description = "Whether current web sources may supplement course PDFs", required = false) Boolean useWeb) {
    return tracker.execute("Adaptive Test Skill", "startAdaptiveTest",
        () -> result(service.start(new LearningDtos.StartRequest(courseId, goal, useWeb))),
        value -> "Started adaptive test session " + value.sessionId());
  }

  @Tool(description = "Create the next adaptive round focused on the current session's weakest knowledge points.")
  public SessionResult continueWeakPointTest(@ToolParam(description = "Learning session ID") UUID sessionId) {
    return tracker.execute("Adaptive Test Skill", "continueWeakPointTest",
        () -> result(service.nextAction(sessionId, LearningStrategy.WEAK_POINTS)),
        value -> "Created weak-point round " + value.roundNumber());
  }

  @Tool(description = "Create the next adaptive round about prerequisite and adjacent topics.")
  public SessionResult testRelatedTopic(@ToolParam(description = "Learning session ID") UUID sessionId) {
    return tracker.execute("Adaptive Test Skill", "testRelatedTopic",
        () -> result(service.nextAction(sessionId, LearningStrategy.RELATED_TOPICS)),
        value -> "Created related-topic round " + value.roundNumber());
  }

  @Tool(description = "Create a harder application and transfer round for an adaptive learning session.")
  public SessionResult increaseTestDifficulty(@ToolParam(description = "Learning session ID") UUID sessionId) {
    return tracker.execute("Adaptive Test Skill", "increaseTestDifficulty",
        () -> result(service.nextAction(sessionId, LearningStrategy.TRANSFER)),
        value -> "Created transfer round " + value.roundNumber());
  }

  @Tool(description = "Stop an adaptive learning session when the user explicitly asks to stop.")
  public SessionResult stopLearningSession(@ToolParam(description = "Learning session ID") UUID sessionId) {
    return tracker.execute("Adaptive Test Skill", "stopLearningSession",
        () -> result(service.stop(sessionId)), value -> "Stopped learning session");
  }

  private SessionResult result(LearningDtos.SessionView session) {
    var current = session.rounds().isEmpty() ? null : session.rounds().getLast();
    return new SessionResult(session.id(), session.status().name(), session.currentRound(),
        current == null ? null : current.id(), current == null ? null : current.quiz().id(),
        "/quiz?sessionId=" + session.id());
  }
  public record SessionResult(UUID sessionId, String status, int roundNumber, UUID roundId, UUID quizId, String actionUrl) { }
}
