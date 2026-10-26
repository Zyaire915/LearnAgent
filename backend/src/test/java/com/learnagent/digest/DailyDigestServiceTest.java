package com.learnagent.digest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.learnagent.analysis.AnalysisService;
import com.learnagent.course.CourseService;
import com.learnagent.job.AgentJobService;
import com.learnagent.learning.LearningSessionRepository;
import com.learnagent.notification.NotificationService;
import com.learnagent.task.StudyTaskRepository;
import com.learnagent.websearch.WebSearchProvider;
import com.learnagent.websearch.WebSearchProvider.WebSearchResult;
import com.learnagent.workspace.WorkspaceFileService;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DailyDigestServiceTest {
  @Test void excludesHistoryBeforeWritingMarkdownAndKeepsCountsConsistentAcrossDays() {
    var digests = mock(DailyDigestRepository.class);
    var resources = mock(ExternalResourceRepository.class);
    var web = mock(WebSearchProvider.class);
    var files = mock(WorkspaceFileService.class);
    var jobs = mock(AgentJobService.class);
    var notifications = mock(NotificationService.class);
    Map<LocalDate, DailyDigest> days = new HashMap<>();
    List<ExternalResource> stored = new ArrayList<>();
    stored.add(new ExternalResource(null, null, "Java 与计算机基础学习", "https://example.org/old", "example", "", null, "old.md"));
    when(digests.findByDigestDate(any())).thenAnswer(call -> Optional.ofNullable(days.get(call.getArgument(0))));
    when(digests.save(any())).thenAnswer(call -> {
      DailyDigest digest = call.getArgument(0); days.put(digest.getDigestDate(), digest); return digest;
    });
    when(resources.findAllByOrderByDiscoveredAtDesc()).thenAnswer(call -> List.copyOf(stored));
    when(resources.save(any())).thenAnswer(call -> { ExternalResource r=call.getArgument(0); stored.add(r); return r; });
    when(resources.findByDigestId(any())).thenAnswer(call -> stored.stream()
        .filter(r -> Objects.equals(r.getDigestId(), call.getArgument(0))).toList());
    when(jobs.run(anyString(), anyString(), any(), any())).thenAnswer(call -> {
      Function<AgentJobService.JobContext, Object> work = call.getArgument(3);
      return work.apply(mock(AgentJobService.JobContext.class));
    });
    when(web.isConfigured()).thenReturn(true);
    // Deliberately return old results: the service must enforce history even for another provider.
    when(web.search(anyString(), eq(5), anyList())).thenAnswer(call -> {
      String query = call.getArgument(0);
      return List.of(new WebSearchResult(query, "", "http://example.org/old?utm_source=test", "example", null),
          new WebSearchResult(query, "", "https://example.org/new", "example", null));
    });
    var service = new DailyDigestService(digests, resources, mock(StudyTaskRepository.class),
        mock(LearningSessionRepository.class), mock(AnalysisService.class), mock(CourseService.class),
        web, files, notifications, jobs, mock(HiddenResourceSourceRepository.class), "Asia/Shanghai");
    LocalDate date = LocalDate.of(2026, 10, 21);
    var first = service.generate(date);
    assertThat(first.resources()).hasSize(1);
    assertThat(first.summary()).contains("1 条", "不足 5 条");
    var markdown = ArgumentCaptor.forClass(String.class);
    verify(files).write(eq("_inbox/2026-10-21/daily-digest.md"), markdown.capture());
    assertThat(markdown.getValue()).contains("https://example.org/new").doesNotContain("example.org/old");
    assertThat(service.generate(date).id()).isEqualTo(first.id());
    verify(web, times(1)).search(anyString(), eq(5), anyList());
    var second = service.generate(date.plusDays(1));
    assertThat(second.resources()).isEmpty();
    assertThat(second.summary()).contains("0 条");
    verify(resources, times(1)).save(any());
    verify(resources, never()).existsByUrl(anyString());
    verify(files).write(eq("_inbox/2026-10-22/daily-digest.md"), markdown.capture());
    assertThat(markdown.getValue()).doesNotContain("https://example.org/new", "example.org/old");
  }
}
