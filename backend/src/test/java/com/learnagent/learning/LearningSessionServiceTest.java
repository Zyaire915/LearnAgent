package com.learnagent.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnagent.course.CourseService;
import com.learnagent.quiz.*;
import com.learnagent.memory.FlashcardService;
import com.learnagent.websearch.WebSearchProvider;
import java.util.*;
import org.junit.jupiter.api.Test;

class LearningSessionServiceTest {
  @Test void requiresOverallEightyAndEveryPointSixty() {
    Fixture value = fixture(List.of(
        result(true, "Processes"), result(true, "Processes"),
        result(true, "Permissions"), result(false, "Permissions"), result(true, "Permissions")));
    LearningDtos.SubmitView submitted = value.service.submit(value.session.getId(), value.round.getId(), new QuizDtos.SubmitRequest(List.of(new QuizDtos.Answer(UUID.randomUUID(), "A"))));
    assertThat(submitted.submission().accuracy()).isEqualTo(.8);
    assertThat(submitted.mastered()).isTrue();
    assertThat(submitted.pointScores().get("Permissions").accuracy()).isGreaterThan(.6);
    assertThat(submitted.recommendations()).hasSize(3);
  }

  @Test void aWeakPointBelowSixtyPreventsMastery() {
    Fixture value = fixture(List.of(
        result(true, "Processes"), result(true, "Processes"), result(true, "Processes"), result(true, "Processes"),
        result(false, "Permissions")));
    LearningDtos.SubmitView submitted = value.service.submit(value.session.getId(), value.round.getId(), new QuizDtos.SubmitRequest(List.of(new QuizDtos.Answer(UUID.randomUUID(), "A"))));
    assertThat(submitted.submission().accuracy()).isEqualTo(.8);
    assertThat(submitted.mastered()).isFalse();
    assertThat(submitted.session().weakPoints()).contains("Permissions");
  }

  private Fixture fixture(List<QuizDtos.Result> results) {
    LearningSessionRepository sessions = mock(LearningSessionRepository.class);
    LearningRoundRepository rounds = mock(LearningRoundRepository.class);
    MasterySnapshotRepository snapshots = mock(MasterySnapshotRepository.class);
    QuizService quizzes = mock(QuizService.class);
    LearningSession session = new LearningSession(UUID.randomUUID(), "OS exam", true);
    session.roundStarted(1, LearningStrategy.DIAGNOSTIC);
    UUID quizId = UUID.randomUUID();
    LearningRound round = new LearningRound(session.getId(), quizId, 1, "OS exam", LearningStrategy.DIAGNOSTIC);
    when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
    when(rounds.findByIdAndSessionId(round.getId(), session.getId())).thenReturn(Optional.of(round));
    when(rounds.findBySessionIdOrderByRoundNumber(session.getId())).thenReturn(List.of(round));
    when(quizzes.submit(eq(quizId), any())).thenReturn(new QuizDtos.Submission(5, (int) results.stream().filter(QuizDtos.Result::correct).count(), results.stream().filter(QuizDtos.Result::correct).count() / 5.0, results));
    when(quizzes.get(quizId)).thenReturn(new QuizDtos.QuizView(quizId, session.getCourseId(), "Test", null, QuizStatus.COMPLETED, session.getId(), 1, List.of()));
    LearningSessionService service = new LearningSessionService(sessions, rounds, snapshots,
        mock(CourseService.class), quizzes, mock(WebSearchProvider.class), mock(FlashcardService.class), new ObjectMapper().findAndRegisterModules());
    return new Fixture(service, session, round);
  }

  private QuizDtos.Result result(boolean correct, String point) {
    return new QuizDtos.Result(UUID.randomUUID(), correct, correct ? "A" : "B", "Explanation", point);
  }
  private record Fixture(LearningSessionService service, LearningSession session, LearningRound round) { }
}
