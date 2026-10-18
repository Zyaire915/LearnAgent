package com.learnagent.weekly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnagent.analysis.AnalysisService;
import com.learnagent.course.CourseService;
import com.learnagent.digest.ExternalResourceRepository;
import com.learnagent.learning.MasterySnapshotRepository;
import com.learnagent.notification.NotificationService;
import com.learnagent.memory.*;
import com.learnagent.quiz.*;
import com.learnagent.task.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

class WeeklyReviewServiceTest {
  private final Instant now = Instant.parse("2026-10-07T04:00:00Z");

  @Test void computesRealStatisticsAndDetailedFallbackWithoutAi() {
    Fixture fixture = fixture();
    when(fixture.tasks.countByCompletedAtGreaterThanEqualAndCompletedAtLessThan(any(), any())).thenReturn(2L);
    when(fixture.tasks.countByStatusInAndCreatedAtLessThan(any(), any())).thenReturn(3L);
    when(fixture.quizzes.countByCompletedAtGreaterThanEqualAndCompletedAtLessThan(any(), any())).thenReturn(1L);
    when(fixture.answers.findByAnsweredAtGreaterThanEqualAndLessThan(any(), any()))
        .thenReturn(List.of(record(true), record(false), record(false)));
    when(fixture.analysis.weakPoints(null)).thenReturn(List.of(new AnalysisService.WeakPoint("Permissions", 5, 2, 3, .4)));
    when(fixture.reviews.save(any())).thenAnswer(call -> call.getArgument(0));
    var review = fixture.service.generate();
    assertThat(review.status()).isEqualTo(WeeklyReviewStatus.PARTIAL);
    assertThat(review.summary()).contains("正确率");
    assertThat(review.statistics().tasksCompleted()).isEqualTo(2);
    assertThat(review.statistics().questionsAnswered()).isEqualTo(3);
    assertThat(review.statistics().overallAccuracy()).isBetween(.3332, .3334);
    assertThat(review.recommendedKnowledgePoints()).containsExactly("Permissions");
    assertThat(review.details().actions()).hasSize(4);
    assertThat(review.memoryStatistics().activeCards()).isEqualTo(4);
    verify(fixture.reviews).save(any(WeeklyReview.class));
    verify(fixture.reviews, never()).deleteAllInBatch();
  }

  @Test void naturalWeekBoundaryIsUsedForAllTimeQueries() {
    Fixture fixture = fixture();
    when(fixture.answers.findByAnsweredAtGreaterThanEqualAndLessThan(any(), any())).thenReturn(List.of());
    when(fixture.analysis.weakPoints(null)).thenReturn(List.of());
    fixture.service.computeSnapshot(Instant.parse("2026-10-05T16:00:00Z"), Instant.parse("2026-10-12T16:00:00Z"));
    verify(fixture.tasks).countByCompletedAtGreaterThanEqualAndCompletedAtLessThan(
        Instant.parse("2026-10-05T16:00:00Z"), Instant.parse("2026-10-12T16:00:00Z"));
    verify(fixture.answers).findByAnsweredAtGreaterThanEqualAndLessThan(
        Instant.parse("2026-10-05T16:00:00Z"), Instant.parse("2026-10-12T16:00:00Z"));
  }

  @Test void schedulerDelegatesToSameService() {
    WeeklyReviewService service = mock(WeeklyReviewService.class);
    NotificationService notifications = mock(NotificationService.class);
    WeeklyReviewService.WeeklyReviewView review = mock(WeeklyReviewService.WeeklyReviewView.class);
    when(service.generate()).thenReturn(review);
    when(review.id()).thenReturn(UUID.randomUUID());
    when(review.periodStart()).thenReturn(now);
    new WeeklyReviewScheduler(service, notifications).generateScheduledReview();
    verify(service).generate();
  }

  private AnswerRecord record(boolean correct) {
    return new AnswerRecord(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
        "Permissions", correct ? "A" : "B", correct);
  }

  private Fixture fixture() {
    WeeklyReviewRepository reviews = mock(WeeklyReviewRepository.class);
    StudyTaskRepository tasks = mock(StudyTaskRepository.class);
    QuizRepository quizzes = mock(QuizRepository.class);
    AnswerRecordRepository answers = mock(AnswerRecordRepository.class);
    AnalysisService analysis = mock(AnalysisService.class);
    CourseService courses = mock(CourseService.class);
    ExternalResourceRepository resources = mock(ExternalResourceRepository.class);
    MasterySnapshotRepository mastery = mock(MasterySnapshotRepository.class);
    MemoryReviewService memory = mock(MemoryReviewService.class);
    when(memory.statistics()).thenReturn(new MemoryDtos.MemoryStatistics(4, 2, 1, .9, 6, 180,
        Map.of(MemoryRating.AGAIN, 1L, MemoryRating.HARD, 1L, MemoryRating.GOOD, 3L, MemoryRating.EASY, 1L), List.of()));
    var chat = new DefaultListableBeanFactory().getBeanProvider(org.springframework.ai.chat.client.ChatClient.Builder.class);
    WeeklyReviewService service = new WeeklyReviewService(
        reviews, tasks, quizzes, answers, analysis, courses, resources, mastery, memory,
        chat, new ObjectMapper().findAndRegisterModules(), Clock.fixed(now, ZoneOffset.UTC), "Asia/Shanghai");
    return new Fixture(service, reviews, tasks, quizzes, answers, analysis);
  }

  private record Fixture(WeeklyReviewService service, WeeklyReviewRepository reviews,
                         StudyTaskRepository tasks, QuizRepository quizzes,
                         AnswerRecordRepository answers, AnalysisService analysis) { }
}
