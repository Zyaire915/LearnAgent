package com.learnagent.memory;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class FsrsSchedulerServiceTest {
  @Test void usesNinetyPercentRetentionAndProducesDistinctIntervals() {
    FsrsSchedulerService service = new FsrsSchedulerService(new ObjectMapper().findAndRegisterModules());
    Instant now = Instant.parse("2026-10-16T00:00:00Z");
    var initial = service.initialize(UUID.fromString("00000000-0000-0000-0000-000000000123"), now);
    Map<MemoryRating, FsrsSchedulerService.ScheduledState> states = new EnumMap<>(MemoryRating.class);
    for (MemoryRating rating : MemoryRating.values()) states.put(rating, service.schedule(initial.cardJson(), rating, now, 8));
    assertThat(service.desiredRetention()).isEqualTo(.90);
    assertThat(states.values()).allSatisfy(value -> {
      assertThat(value.due()).isAfter(now);
      assertThat(value.retrievability()).isBetween(0.0, 1.0);
    });
    assertThat(states.values().stream().map(FsrsSchedulerService.ScheduledState::due).distinct()).hasSize(4);
    assertThat(states.get(MemoryRating.AGAIN).due()).isBefore(states.get(MemoryRating.GOOD).due());
    assertThat(states.get(MemoryRating.GOOD).due()).isBefore(states.get(MemoryRating.EASY).due());
  }
}
