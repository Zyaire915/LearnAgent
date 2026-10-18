package com.learnagent.weekly;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnagent.analysis.AnalysisService;
import com.learnagent.course.*;
import com.learnagent.digest.ExternalResourceRepository;
import com.learnagent.learning.MasterySnapshotRepository;
import com.learnagent.memory.MemoryDtos;
import com.learnagent.memory.MemoryRating;
import com.learnagent.memory.MemoryReviewService;
import com.learnagent.quiz.*;
import com.learnagent.task.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WeeklyReviewService {
  private final WeeklyReviewRepository reviews;
  private final StudyTaskRepository tasks;
  private final QuizRepository quizzes;
  private final AnswerRecordRepository answers;
  private final AnalysisService analysis;
  private final CourseService courses;
  private final ExternalResourceRepository resources;
  private final MasterySnapshotRepository mastery;
  private final MemoryReviewService memory;
  private final ObjectProvider<ChatClient.Builder> chat;
  private final ObjectMapper json;
  private final Clock clock;
  private final ZoneId zone;

  public WeeklyReviewService(WeeklyReviewRepository reviews, StudyTaskRepository tasks, QuizRepository quizzes,
      AnswerRecordRepository answers, AnalysisService analysis, CourseService courses,
      ExternalResourceRepository resources, MasterySnapshotRepository mastery, MemoryReviewService memory,
      ObjectProvider<ChatClient.Builder> chat, ObjectMapper json, Clock clock,
      @Value("${learnagent.time-zone:Asia/Shanghai}") String zone) {
    this.reviews = reviews; this.tasks = tasks; this.quizzes = quizzes; this.answers = answers;
    this.analysis = analysis; this.courses = courses; this.resources = resources; this.mastery = mastery;
    this.memory = memory; this.chat = chat; this.json = json; this.clock = clock; this.zone = ZoneId.of(zone);
  }

  @Transactional
  public WeeklyReviewView generate() {
    Period period = period();
    Instant now = clock.instant();
    WeeklyLearningSnapshot snapshot = computeSnapshot(period.start(), period.end());
    List<String> recommended = snapshot.weakPoints().stream()
        .sorted(Comparator.comparingDouble(WeeklyLearningSnapshot.WeakPointSnapshot::accuracy)).limit(3)
        .map(WeeklyLearningSnapshot.WeakPointSnapshot::knowledgePoint).toList();
    WeeklyDetails details = details(period, snapshot, recommended);
    MemoryDtos.MemoryStatistics memoryStatistics = memory.statistics();
    Narrative narrative = narrative(snapshot, details, memoryStatistics);
    WeeklyReviewStatus status = narrative == null ? WeeklyReviewStatus.PARTIAL : WeeklyReviewStatus.COMPLETE;
    WeeklyReview review = reviews.save(new WeeklyReview(period.start(), period.end(), now,
        narrative == null ? details.reflection() : narrative.summary(),
        narrative == null ? String.join("；", details.nextWeekPlan()) : narrative.recommendation(),
        status, write(snapshot), write(recommended), write(details), write(memoryStatistics)));
    return view(review);
  }

  @Transactional(readOnly = true)
  public boolean existsCurrentWeek() { Period p = period(); return reviews.existsByPeriodStartAndPeriodEnd(p.start(), p.end()); }
  @Transactional(readOnly = true)
  public List<WeeklyReviewView> list() { return reviews.findAllByOrderByGeneratedAtDesc().stream().map(this::view).toList(); }
  @Transactional(readOnly = true)
  public WeeklyReviewView latest() { return reviews.findFirstByOrderByGeneratedAtDesc().map(this::view).orElse(null); }
  @Transactional(readOnly = true)
  public WeeklyReviewView get(UUID id) { return reviews.findById(id).map(this::view).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Weekly review not found.")); }

  WeeklyLearningSnapshot computeSnapshot(Instant start, Instant end) {
    long completed = tasks.countByCompletedAtGreaterThanEqualAndCompletedAtLessThan(start, end);
    long pending = tasks.countByStatusInAndCreatedAtLessThan(List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS), end);
    long quizCount = quizzes.countByCompletedAtGreaterThanEqualAndCompletedAtLessThan(start, end);
    List<AnswerRecord> records = answers.findByAnsweredAtGreaterThanEqualAndLessThan(start, end);
    long correct = records.stream().filter(AnswerRecord::isCorrect).count();
    double accuracy = records.isEmpty() ? 0 : (double) correct / records.size();
    List<WeeklyLearningSnapshot.WeakPointSnapshot> weak = analysis.weakPoints(null).stream()
        .map(point -> new WeeklyLearningSnapshot.WeakPointSnapshot(point.knowledgePoint(), point.attemptCount(), point.accuracy())).toList();
    return new WeeklyLearningSnapshot(completed, pending, quizCount, records.size(), correct, accuracy, weak);
  }

  private WeeklyDetails details(Period period, WeeklyLearningSnapshot snapshot, List<String> recommended) {
    Instant now = clock.instant();
    List<StudyTask> allTasks = tasks.findAll();
    long overdue = allTasks.stream().filter(task -> task.getStatus() != TaskStatus.COMPLETED && task.getDueAt() != null && task.getDueAt().isBefore(now)).count();
    long createdBeforeEnd = allTasks.stream().filter(task -> task.getCreatedAt() != null && task.getCreatedAt().isBefore(period.end())).count();
    double completionRate = createdBeforeEnd == 0 ? 0 : (double) snapshot.tasksCompleted() / createdBeforeEnd;
    List<CourseProgress> courseProgress = new ArrayList<>();
    for (Course course : courses.list()) {
      List<AnswerRecord> records = answers.findByCourseId(course.getId()).stream()
          .filter(a -> !a.getAnsweredAt().isBefore(period.start()) && a.getAnsweredAt().isBefore(period.end())).toList();
      long correct = records.stream().filter(AnswerRecord::isCorrect).count();
      long coursePending = allTasks.stream().filter(t -> course.getId().equals(t.getCourseId()) && t.getStatus() != TaskStatus.COMPLETED).count();
      courseProgress.add(new CourseProgress(course.getId(), course.getName(), records.size(), records.isEmpty() ? 0 : (double) correct / records.size(), coursePending));
    }
    Map<String, Double> masteryByPoint = new LinkedHashMap<>();
    mastery.findAll().forEach(item -> masteryByPoint.put(item.getKnowledgePoint(), item.getAccuracy()));
    long resourceCount = resources.countByDiscoveredAtGreaterThanEqualAndDiscoveredAtLessThan(period.start(), period.end());
    List<String> accomplishments = new ArrayList<>();
    if (snapshot.tasksCompleted() > 0) accomplishments.add("完成 " + snapshot.tasksCompleted() + " 个学习任务");
    if (snapshot.questionsAnswered() > 0) accomplishments.add("完成 " + snapshot.questionsAnswered() + " 道题，正确率 " + Math.round(snapshot.overallAccuracy() * 100) + "%");
    if (resourceCount > 0) accomplishments.add("收集并整理 " + resourceCount + " 条外部学习资料");
    if (accomplishments.isEmpty()) accomplishments.add("本周已有学习记录，但尚未形成完成项");
    List<String> plan = recommended.isEmpty()
        ? List.of("完成最临近截止日期的任务", "开始一轮5题诊断测试", "阅读每日推荐资料并记录要点")
        : recommended.stream().map(point -> "围绕“" + point + "”完成一轮强化测试").toList();
    String reflection = snapshot.questionsAnswered() == 0
        ? "本周缺少可量化的测验记录，下周先完成一次诊断测试。"
        : "本周总体正确率为 " + Math.round(snapshot.overallAccuracy() * 100) + "%，应优先处理正确率最低的知识点。";
    return new WeeklyDetails(completionRate, overdue, resourceCount, courseProgress, masteryByPoint, accomplishments,
        reflection, plan, List.of(new Action("创建任务", "/tasks"), new Action("开始强化测试", "/quiz"),
            new Action("打开每日资料", "/workbench?path=_inbox"), new Action("开始记忆复习", "/memory")));
  }

  private Narrative narrative(WeeklyLearningSnapshot snapshot, WeeklyDetails details, MemoryDtos.MemoryStatistics memoryStatistics) {
    ChatClient.Builder builder = availableBuilder();
    if (builder == null) return null;
    try {
      return builder.build().prompt()
          .system("根据真实统计撰写详细中文学习周报。不得编造数字；总结任务、测验、复习留存、薄弱点，并给出可执行的下周建议。")
          .user("Statistics: " + write(snapshot) + "\nMemory: " + write(memoryStatistics) + "\nDetails: " + write(details))
          .call().entity(Narrative.class);
    } catch (RuntimeException ignored) { return null; }
  }

  private Period period() {
    ZonedDateTime zoned = clock.instant().atZone(zone);
    Instant start = zoned.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant();
    return new Period(start, start.atZone(zone).plusWeeks(1).toInstant());
  }
  private ChatClient.Builder availableBuilder() { try { return chat.getIfAvailable(); } catch (BeansException exception) { return null; } }
  private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception exception) { throw new IllegalStateException("Could not serialize weekly review data.", exception); } }

  private WeeklyReviewView view(WeeklyReview review) {
    try {
      return new WeeklyReviewView(review.getId(), review.getPeriodStart(), review.getPeriodEnd(), review.getGeneratedAt(),
          review.getSummary(), review.getRecommendation(), review.getStatus(),
          json.readValue(review.getStatisticsJson(), WeeklyLearningSnapshot.class),
          json.readValue(review.getRecommendedKnowledgePointsJson(), new TypeReference<List<String>>() {}),
          json.readValue(review.getDetailsJson(), WeeklyDetails.class), readMemory(review.getMemoryStatisticsJson()));
    } catch (Exception exception) { throw new IllegalStateException("Stored weekly review data is invalid.", exception); }
  }

  private MemoryDtos.MemoryStatistics readMemory(String value) throws Exception {
    if (value == null || value.isBlank() || "{}".equals(value.trim())) {
      Map<MemoryRating, Long> distribution = new EnumMap<>(MemoryRating.class);
      for (MemoryRating rating : MemoryRating.values()) distribution.put(rating, 0L);
      return new MemoryDtos.MemoryStatistics(0, 0, 0, 0, 0, 0, distribution, List.of());
    }
    return json.readValue(value, MemoryDtos.MemoryStatistics.class);
  }

  private record Period(Instant start, Instant end) { }
  public record Narrative(String summary, String recommendation) { }
  public record CourseProgress(UUID courseId, String courseName, long questionsAnswered, double accuracy, long pendingTasks) { }
  public record Action(String label, String url) { }
  public record WeeklyDetails(double taskCompletionRate, long overdueTasks, long externalResources,
      List<CourseProgress> courseProgress, Map<String, Double> masteryByPoint, List<String> accomplishments,
      String reflection, List<String> nextWeekPlan, List<Action> actions) { }
  public record WeeklyReviewView(UUID id, Instant periodStart, Instant periodEnd, Instant generatedAt, String summary,
      String recommendation, WeeklyReviewStatus status, WeeklyLearningSnapshot statistics,
      List<String> recommendedKnowledgePoints, WeeklyDetails details, MemoryDtos.MemoryStatistics memoryStatistics) { }
}
