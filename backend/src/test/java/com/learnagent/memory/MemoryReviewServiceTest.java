package com.learnagent.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class MemoryReviewServiceTest {
  private final Instant now = Instant.parse("2026-10-16T00:00:00Z");

  @Test void dailyQueueIsCappedAtTwentyAndOldestDueWinsWhenRetrievabilityMatches() {
    FlashcardRepository cards = mock(FlashcardRepository.class);
    FlashcardReviewStateRepository states = mock(FlashcardReviewStateRepository.class);
    FlashcardService flashcards = mock(FlashcardService.class);
    FsrsSchedulerService fsrs = new FsrsSchedulerService(new ObjectMapper().findAndRegisterModules());
    UUID courseId = UUID.randomUUID();
    List<Flashcard> cardValues = new ArrayList<>();
    List<FlashcardReviewState> stateValues = new ArrayList<>();
    for (int index = 0; index < 25; index++) {
      Flashcard card = new Flashcard(courseId, UUID.randomUUID(), UUID.randomUUID(), null,
          FlashcardType.BASIC, "Question " + index, "Answer", "Point", "[{\"type\":\"QUIZ\",\"title\":\"quiz\"}]", "hash" + index);
      card.approve();
      cardValues.add(card);
      var initial = fsrs.initialize(card.getId(), now.minus(Duration.ofDays(index + 1)));
      stateValues.add(new FlashcardReviewState(card.getId(), initial.cardJson(), now.minus(Duration.ofDays(index + 1))));
      when(flashcards.get(card.getId())).thenReturn(new MemoryDtos.CardView(card.getId(), courseId,
          FlashcardType.BASIC, card.getFront(), card.getBack(), card.getKnowledgePoint(),
          FlashcardStatus.ACTIVE, List.of(), now, now, null));
    }
    when(states.findByDueAtLessThanEqualOrderByRetrievabilityAscDueAtAsc(now)).thenReturn(stateValues);
    when(cards.findById(any())).thenAnswer(call -> cardValues.stream()
        .filter(card -> card.getId().equals(call.getArgument(0))).findFirst());
    MemoryReviewService service = new MemoryReviewService(cards, states, mock(FlashcardReviewLogRepository.class),
        mock(MemoryReviewSessionRepository.class), flashcards, fsrs, mock(KnowledgeGraphService.class),
        Clock.fixed(now, ZoneOffset.UTC), "Asia/Shanghai");

    List<MemoryDtos.CardView> due = service.due(courseId);

    assertThat(due).hasSize(20);
    assertThat(due.getFirst().front()).isEqualTo("Question 24");
    assertThat(due).extracting(MemoryDtos.CardView::id).doesNotHaveDuplicates();
  }

  @Test void sessionStopsAtFifteenMinutes() {
    MemoryReviewSession session = new MemoryReviewSession(LocalDate.of(2026, 10, 16), 20);
    session.record(MemoryRating.GOOD, 899);
    assertThat(session.getStatus()).isEqualTo(MemoryReviewSessionStatus.ACTIVE);
    session.record(MemoryRating.AGAIN, 1);
    assertThat(session.getStatus()).isEqualTo(MemoryReviewSessionStatus.COMPLETED);
    assertThat(session.getElapsedSeconds()).isEqualTo(900);
  }
}
