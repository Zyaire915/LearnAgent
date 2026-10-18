package com.learnagent.learning;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnagent.course.CourseService;
import com.learnagent.quiz.Quiz;
import com.learnagent.quiz.QuizDtos;
import com.learnagent.quiz.QuizService;
import com.learnagent.memory.FlashcardService;
import com.learnagent.websearch.WebSearchProvider;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LearningSessionService {
  private final LearningSessionRepository sessions;
  private final LearningRoundRepository rounds;
  private final MasterySnapshotRepository snapshots;
  private final CourseService courses;
  private final QuizService quizzes;
  private final WebSearchProvider web;
  private final FlashcardService flashcards;
  private final ObjectMapper json;

  public LearningSessionService(
      LearningSessionRepository sessions,
      LearningRoundRepository rounds,
      MasterySnapshotRepository snapshots,
      CourseService courses,
      QuizService quizzes,
      WebSearchProvider web,
      FlashcardService flashcards,
      ObjectMapper json) {
    this.sessions = sessions; this.rounds = rounds; this.snapshots = snapshots;
    this.courses = courses; this.quizzes = quizzes; this.web = web; this.flashcards = flashcards; this.json = json;
  }

  @Transactional
  public LearningDtos.SessionView start(LearningDtos.StartRequest request) {
    courses.get(request.courseId());
    LearningSession session = sessions.save(new LearningSession(
        request.courseId(), request.goal().trim(), request.webEnabled() == null || request.webEnabled()));
    createRound(session, LearningStrategy.DIAGNOSTIC, request.goal().trim());
    return view(session);
  }

  @Transactional(readOnly = true)
  public LearningDtos.SessionView get(UUID id) { return view(required(id)); }

  @Transactional(readOnly = true)
  public List<LearningDtos.SessionView> list(UUID courseId) {
    return (courseId == null ? sessions.findAllByOrderByUpdatedAtDesc()
        : sessions.findByCourseIdOrderByUpdatedAtDesc(courseId)).stream().map(this::view).toList();
  }

  @Transactional
  public LearningDtos.RoundView createRound(UUID sessionId, LearningStrategy strategy) {
    LearningSession session = required(sessionId);
    String focus = focusFor(session, strategy);
    return roundView(createRound(session, strategy, focus));
  }

  @Transactional
  public LearningDtos.SubmitView submit(
      UUID sessionId, UUID roundId, QuizDtos.SubmitRequest request) {
    LearningSession session = required(sessionId);
    LearningRound round = rounds.findByIdAndSessionId(roundId, sessionId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Learning round not found."));
    if (round.getSubmittedAt() != null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "This learning round has already been submitted.");
    }

    QuizDtos.Submission submission = quizzes.submit(round.getQuizId(), request);
    int candidateFlashcards = flashcards.fromSubmission(round.getQuizId(), submission);
    Map<String, MutableScore> aggregate = new LinkedHashMap<>();
    submission.results().forEach(result -> aggregate
        .computeIfAbsent(result.knowledgePoint(), ignored -> new MutableScore())
        .add(result.correct()));
    Map<String, LearningDtos.PointScore> scores = new LinkedHashMap<>();
    aggregate.forEach((point, score) -> scores.put(point, score.view()));
    boolean mastered = submission.accuracy() >= session.getMasteryThreshold()
        && !scores.isEmpty()
        && scores.values().stream().allMatch(score -> score.accuracy() >= session.getPointFloor());
    List<String> weakPoints = scores.entrySet().stream()
        .filter(entry -> entry.getValue().accuracy() < session.getPointFloor())
        .sorted(Map.Entry.comparingByValue(Comparator.comparingDouble(LearningDtos.PointScore::accuracy)))
        .map(Map.Entry::getKey).toList();

    for (var entry : aggregate.entrySet()) {
      snapshots.save(new MasterySnapshot(
          sessionId, roundId, entry.getKey(), entry.getValue().attempts, entry.getValue().correct));
    }
    List<LearningDtos.Recommendation> recommendations = recommendations(session, weakPoints);
    round.complete(submission.accuracy(), mastered, write(scores), write(recommendations));
    session.roundCompleted(mastered, write(weakPoints));
    return new LearningDtos.SubmitView(
        submission, scores, mastered, session.getCurrentRound() < session.getMaxRounds(), candidateFlashcards,
        recommendations, view(session));
  }

  @Transactional
  public LearningDtos.SessionView nextAction(UUID sessionId, LearningStrategy strategy) {
    LearningSession session = required(sessionId);
    if (strategy == LearningStrategy.STOP) {
      session.stop();
      return view(session);
    }
    createRound(session, strategy, focusFor(session, strategy));
    return view(session);
  }

  @Transactional
  public LearningDtos.SessionView stop(UUID sessionId) {
    LearningSession session = required(sessionId); session.stop(); return view(session);
  }

  private LearningRound createRound(LearningSession session, LearningStrategy strategy, String focus) {
    if (session.getStatus() == LearningSessionStatus.STOPPED) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Learning session is stopped.");
    }
    int number = session.getCurrentRound() + 1;
    if (number > session.getMaxRounds()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "The three-round limit has been reached.");
    }
    List<WebSearchProvider.WebSearchResult> external = List.of();
    if (session.isWebEnabled() && web.isConfigured()) {
      try { external = web.search(session.getGoal() + " " + focus, 3); }
      catch (RuntimeException ignored) { external = List.of(); }
    }
    Quiz quiz = quizzes.generateAdaptive(
        new QuizDtos.GenerateRequest(session.getCourseId(), focus, 5), session.getId(), number, external);
    LearningRound round = rounds.save(new LearningRound(session.getId(), quiz.getId(), number, focus, strategy));
    session.roundStarted(number, strategy);
    return round;
  }

  private String focusFor(LearningSession session, LearningStrategy strategy) {
    List<String> weak = read(session.getWeakPointsJson(), new TypeReference<>() { }, List.of());
    return switch (strategy) {
      case WEAK_POINTS -> weak.isEmpty() ? session.getGoal() : String.join("、", weak);
      case RELATED_TOPICS -> session.getGoal() + " 的前置知识与相邻知识点";
      case TRANSFER -> session.getGoal() + " 的应用、分析与迁移";
      case DIAGNOSTIC -> session.getGoal();
      case STOP -> session.getGoal();
    };
  }

  private List<LearningDtos.Recommendation> recommendations(LearningSession session, List<String> weak) {
    boolean enabled = session.getCurrentRound() < session.getMaxRounds();
    String point = weak.isEmpty() ? "本轮知识点" : String.join("、", weak);
    return List.of(
        new LearningDtos.Recommendation("WEAK_POINTS", "强化薄弱点", "围绕“" + point + "”再练一轮。", enabled),
        new LearningDtos.Recommendation("RELATED_TOPICS", "关联知识测试", "检查前置知识和相邻板块。", enabled),
        new LearningDtos.Recommendation("TRANSFER", "提高难度", "进入应用、分析与迁移题。", enabled));
  }

  private LearningSession required(UUID id) {
    return sessions.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Learning session not found."));
  }

  private LearningDtos.SessionView view(LearningSession session) {
    List<LearningDtos.RoundView> roundViews = rounds.findBySessionIdOrderByRoundNumber(session.getId())
        .stream().map(this::roundView).toList();
    return new LearningDtos.SessionView(
        session.getId(), session.getCourseId(), session.getGoal(), session.getStatus(),
        session.getCurrentRound(), session.getMaxRounds(), session.getMasteryThreshold(), session.getPointFloor(),
        session.isWebEnabled(), read(session.getWeakPointsJson(), new TypeReference<>() { }, List.of()),
        roundViews, session.getCreatedAt(), session.getUpdatedAt(), session.getCompletedAt());
  }

  private LearningDtos.RoundView roundView(LearningRound round) {
    return new LearningDtos.RoundView(
        round.getId(), round.getRoundNumber(), round.getFocus(), round.getStrategy(), round.getOverallAccuracy(),
        round.isMastered(), read(round.getPointScoresJson(), new TypeReference<>() { }, Map.of()),
        read(round.getRecommendationsJson(), new TypeReference<>() { }, List.of()),
        round.getCreatedAt(), round.getSubmittedAt(), quizzes.get(round.getQuizId()));
  }

  private String write(Object value) {
    try { return json.writeValueAsString(value); }
    catch (Exception exception) { throw new IllegalStateException("Could not serialize learning session data.", exception); }
  }

  private <T> T read(String value, TypeReference<T> type, T fallback) {
    if (value == null || value.isBlank()) return fallback;
    try { return json.readValue(value, type); }
    catch (Exception ignored) { return fallback; }
  }

  private static final class MutableScore {
    private int attempts;
    private int correct;
    void add(boolean value) { attempts++; if (value) correct++; }
    LearningDtos.PointScore view() {
      return new LearningDtos.PointScore(attempts, correct, attempts == 0 ? 0 : (double) correct / attempts);
    }
  }
}
