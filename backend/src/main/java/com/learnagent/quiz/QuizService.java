package com.learnagent.quiz;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnagent.course.Course;
import com.learnagent.course.CourseService;
import com.learnagent.rag.AiFeatureUnavailableException;
import com.learnagent.rag.LocalKnowledgeService;
import com.learnagent.websearch.WebSearchProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QuizService {
  private static final String DEFAULT_KNOWLEDGE_POINT = "综合练习";
  private static final String QUIZ_SYSTEM_PROMPT = """
      你是一名严谨的课程教师。只能根据提供的课程资料或带来源的网络资料生成中文单选题。
      必须严格满足以下要求：
      1. 精确生成指定数量的题目；
      2. 每题恰好有 4 个非空且互不重复的选项，options 必须是 JSON 字符串数组，
         例如 ["选项一", "选项二", "选项三", "选项四"]；
      3. correctAnswer 必须完整复制 options 中某一个选项，不能只写 A、B、C、D；
      4. explanation 和 knowledgePoint 都不能为空；
      5. 不得使用课程资料之外的事实；
      6. 题干、选项和解释保持简洁，解释控制在 80 个汉字以内，避免结构化输出被截断。
      """;

  private final QuizRepository quizzes;
  private final QuestionRepository questions;
  private final AnswerRecordRepository answers;
  private final CourseService courses;
  private final LocalKnowledgeService knowledge;
  private final ObjectProvider<ChatClient.Builder> chat;
  private final ObjectMapper json;

  public QuizService(
      QuizRepository quizzes,
      QuestionRepository questions,
      AnswerRecordRepository answers,
      CourseService courses,
      LocalKnowledgeService knowledge,
      ObjectProvider<ChatClient.Builder> chat,
      ObjectMapper json) {
    this.quizzes = quizzes;
    this.questions = questions;
    this.answers = answers;
    this.courses = courses;
    this.knowledge = knowledge;
    this.chat = chat;
    this.json = json;
  }

  @Transactional
  public Quiz generate(QuizDtos.GenerateRequest request) {
    return generateInternal(request, null, null, List.of());
  }

  @Transactional
  public Quiz generateAdaptive(
      QuizDtos.GenerateRequest request,
      UUID sessionId,
      int roundNumber,
      List<WebSearchProvider.WebSearchResult> externalSources) {
    return generateInternal(request, sessionId, roundNumber, externalSources == null ? List.of() : externalSources);
  }

  private Quiz generateInternal(
      QuizDtos.GenerateRequest request,
      UUID sessionId,
      Integer roundNumber,
      List<WebSearchProvider.WebSearchResult> externalSources) {
    Course course = courses.get(request.courseId());
    ChatClient.Builder builder = chat.getIfAvailable();
    if (builder == null) {
      throw new AiFeatureUnavailableException(
          "Quiz generation is not configured. Configure Chat, Embedding and PGVector providers first.");
    }

    String requestedPoint = clean(request.knowledgePoint());
    var excerpts = knowledge.search(
        request.courseId(), requestedPoint == null ? "course concepts" : requestedPoint);
    if (excerpts.isEmpty() && externalSources.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "所选课程和主题暂时没有可用资料。请上传课程资料，或使用带网络资料的自适应测试。");
    }

    int desiredCount = request.questionCount() == null ? 5 : request.questionCount();
    String context = "课程：" + course.getName() + "\n课程描述："
        + Optional.ofNullable(course.getDescription()).orElse("未填写") + "\n" + excerpts.stream()
        .map(document -> truncate(document.getText(), 2500))
        .reduce("", (left, right) -> left + "\n" + right);
    if (!externalSources.isEmpty()) {
      context += "\n外部公开资料摘要：\n" + externalSources.stream()
          .map(source -> source.title() + "\n" + truncate(source.snippet(), 1000) + "\n来源：" + source.url())
          .reduce("", (left, right) -> left + "\n" + right);
    }
    context = truncate(context, 16000);
    String sourcesJson = sourceSnapshot(excerpts, externalSources);
    QuizDtos.GeneratedQuiz generated = null;
    ResponseStatusException validationFailure = null;

    for (int attempt = 1; attempt <= 2; attempt++) {
      try {
        generated = requestQuiz(builder, desiredCount, requestedPoint, context, attempt > 1);
        generated = normalize(generated, requestedPoint);
        validate(generated);
        if (generated.questions().size() != desiredCount) {
          throw invalid("expected " + desiredCount + " questions but received " + generated.questions().size());
        }
        validationFailure = null;
        break;
      } catch (ResponseStatusException exception) {
        validationFailure = exception;
      } catch (RuntimeException exception) {
        validationFailure = invalid("model returned malformed structured output");
      }
    }

    if (validationFailure != null) {
      throw validationFailure;
    }

    Quiz quiz = new Quiz(request.courseId(), generated.title(), requestedPoint);
    quiz.attachLearningContext(sessionId, roundNumber, sourcesJson);
    quiz = quizzes.save(quiz);
    for (var item : generated.questions()) {
      questions.save(new Question(
          quiz.getId(), item.content(), toJson(item.options()), item.correctAnswer(),
          item.explanation(), item.knowledgePoint(), sourcesJson));
    }
    return quiz;
  }

  private QuizDtos.GeneratedQuiz requestQuiz(
      ChatClient.Builder builder,
      int desiredCount,
      String requestedPoint,
      String context,
      boolean retry) {
    String focus = requestedPoint == null ? "覆盖资料中的主要知识点" : "聚焦知识点：“" + requestedPoint + "”";
    String retryInstruction = retry
        ? "上一次结果未通过结构校验。请重新生成，并特别检查每道题的 options 都是包含四个字符串的 JSON 数组。\n"
        : "";
    return builder.build().prompt()
        .system(QUIZ_SYSTEM_PROMPT)
        .user(retryInstruction + "请生成 " + desiredCount + " 道题，" + focus + "。\n课程资料：\n" + context)
        .call()
        .entity(QuizDtos.GeneratedQuiz.class);
  }

  QuizDtos.GeneratedQuiz normalize(QuizDtos.GeneratedQuiz generated, String requestedPoint) {
    if (generated == null || generated.questions() == null) {
      return generated;
    }
    String title = clean(generated.title());
    if (title == null) {
      title = requestedPoint == null ? "课程资料综合测验" : requestedPoint + "测验";
    }

    List<QuizDtos.GeneratedQuestion> normalized = new ArrayList<>();
    for (QuizDtos.GeneratedQuestion question : generated.questions()) {
      if (question == null) {
        normalized.add(null);
        continue;
      }
      List<String> options = normalizeOptions(question.options());
      String correctAnswer = resolveCorrectAnswer(options, question.correctAnswer());
      String point = Optional.ofNullable(clean(question.knowledgePoint()))
          .orElse(requestedPoint == null ? DEFAULT_KNOWLEDGE_POINT : requestedPoint);
      String explanation = clean(question.explanation());
      if (explanation == null && correctAnswer != null) {
        explanation = "正确答案是：“" + correctAnswer + "”。";
      }
      normalized.add(new QuizDtos.GeneratedQuestion(
          clean(question.content()), options, correctAnswer, explanation, point));
    }
    return new QuizDtos.GeneratedQuiz(title, Collections.unmodifiableList(normalized));
  }

  private List<String> normalizeOptions(List<String> rawOptions) {
    if (rawOptions == null) {
      return null;
    }
    LinkedHashSet<String> values = new LinkedHashSet<>();
    for (String option : rawOptions) {
      String value = clean(option);
      if (value != null) {
        values.add(value);
      }
    }
    return List.copyOf(values);
  }

  private String resolveCorrectAnswer(List<String> options, String rawAnswer) {
    if (options == null || options.isEmpty()) {
      return clean(rawAnswer);
    }
    String answer = clean(rawAnswer);
    if (answer == null) {
      return null;
    }
    for (String option : options) {
      if (option.equals(answer)) {
        return option;
      }
    }

    String upper = answer.toUpperCase(Locale.ROOT);
    if (upper.matches("^[A-D]$")) {
      int index = upper.charAt(0) - 'A';
      return index < options.size() ? options.get(index) : answer;
    }
    if (upper.matches("^[A-D][\\.、:：)）].*")) {
      int index = upper.charAt(0) - 'A';
      return index < options.size() ? options.get(index) : answer;
    }

    String strippedAnswer = stripOptionLabel(answer);
    for (String option : options) {
      if (stripOptionLabel(option).equalsIgnoreCase(strippedAnswer)) {
        return option;
      }
    }
    return answer;
  }

  private String stripOptionLabel(String value) {
    return value.replaceFirst("(?i)^\\s*[A-D][\\.、:：)）]\\s*", "").trim();
  }

  private String clean(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String truncate(String value, int maxLength) {
    if (value == null) return "";
    String clean = value.trim();
    return clean.length() <= maxLength ? clean : clean.substring(0, maxLength) + "…";
  }

  @Transactional(readOnly = true)
  public QuizDtos.QuizView get(UUID id) {
    Quiz quiz = quizzes.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz not found."));
    return view(quiz);
  }

  @Transactional
  public QuizDtos.Submission submit(UUID id, QuizDtos.SubmitRequest request) {
    Quiz quiz = quizzes.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz not found."));
    if (answers.existsByQuizId(id)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "This quiz has already been submitted.");
    }
    List<Question> storedQuestions = questions.findByQuizId(id);
    Map<UUID, String> submitted = new HashMap<>();
    request.answers().forEach(answer -> submitted.put(answer.questionId(), answer.answer()));
    if (submitted.size() != storedQuestions.size()
        || !submitted.keySet().containsAll(storedQuestions.stream().map(Question::getId).toList())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Answer every question exactly once.");
    }

    List<QuizDtos.Result> results = new ArrayList<>();
    int correctCount = 0;
    for (Question question : storedQuestions) {
      String answer = submitted.get(question.getId());
      boolean correct = question.getCorrectAnswer().equals(answer);
      if (correct) {
        correctCount++;
      }
      answers.save(new AnswerRecord(
          quiz.getId(), question.getId(), quiz.getCourseId(), question.getKnowledgePoint(), answer, correct));
      results.add(new QuizDtos.Result(
          question.getId(), correct, question.getCorrectAnswer(), question.getExplanation(), question.getKnowledgePoint()));
    }
    quiz.complete();
    return new QuizDtos.Submission(
        storedQuestions.size(), correctCount,
        storedQuestions.isEmpty() ? 0 : (double) correctCount / storedQuestions.size(), results);
  }

  @Transactional(readOnly = true)
  public List<QuizDtos.QuizView> list(UUID courseId) {
    return (courseId == null ? quizzes.findAll() : quizzes.findByCourseId(courseId))
        .stream().map(this::view).toList();
  }

  @Transactional(readOnly = true)
  public List<QuizDtos.HistoryView> history(UUID courseId) {
    return (courseId == null ? quizzes.findAll() : quizzes.findByCourseId(courseId)).stream()
        .filter(quiz -> quiz.getStatus() == QuizStatus.COMPLETED)
        .sorted((left, right) -> right.getCompletedAt().compareTo(left.getCompletedAt()))
        .map(quiz -> {
          List<AnswerRecord> records = answers.findByQuizIdOrderByAnsweredAtAsc(quiz.getId());
          int total = records.size();
          int correct = (int) records.stream().filter(AnswerRecord::isCorrect).count();
          return new QuizDtos.HistoryView(quiz.getId(), quiz.getCourseId(), quiz.getTitle(),
              quiz.getKnowledgePoint(), quiz.getStatus(), quiz.getLearningSessionId(),
              quiz.getLearningRoundNumber(), quiz.getCreatedAt(), quiz.getCompletedAt(), total,
              correct, total == 0 ? 0 : (double) correct / total);
        }).toList();
  }

  @Transactional(readOnly = true)
  public QuizDtos.HistoryDetail historyDetail(UUID id) {
    Quiz quiz = quizzes.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz not found."));
    List<AnswerRecord> records = answers.findByQuizIdOrderByAnsweredAtAsc(id);
    if (quiz.getStatus() != QuizStatus.COMPLETED || records.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Quiz has no completed result yet.");
    }
    List<QuizDtos.Result> results = new ArrayList<>();
    List<QuizDtos.HistoryAnswer> historyAnswers = new ArrayList<>();
    for (AnswerRecord record : records) {
      Question question = questions.findById(record.getQuestionId()).orElse(null);
      String correctAnswer = question == null ? null : question.getCorrectAnswer();
      String explanation = question == null ? null : question.getExplanation();
      results.add(new QuizDtos.Result(record.getQuestionId(), record.isCorrect(), correctAnswer,
          explanation, record.getKnowledgePoint()));
      historyAnswers.add(new QuizDtos.HistoryAnswer(record.getQuestionId(), record.getUserAnswer(),
          record.isCorrect(), correctAnswer, explanation, record.getKnowledgePoint()));
    }
    int correct = (int) records.stream().filter(AnswerRecord::isCorrect).count();
    return new QuizDtos.HistoryDetail(view(quiz), records.size(), correct,
        (double) correct / records.size(), results, historyAnswers);
  }

  @Transactional(readOnly = true)
  public List<WrongAnswer> getWrongAnswers(UUID courseId) {
    return answers.findByCourseId(courseId).stream()
        .filter(answer -> !answer.isCorrect())
        .map(answer -> {
          Question question = questions.findById(answer.getQuestionId()).orElse(null);
          return new WrongAnswer(
              answer.getQuizId(), answer.getQuestionId(),
              question == null ? "Question unavailable" : question.getContent(),
              answer.getUserAnswer(), question == null ? null : question.getCorrectAnswer(),
              answer.getKnowledgePoint());
        }).toList();
  }

  private QuizDtos.QuizView view(Quiz quiz) {
    return new QuizDtos.QuizView(
        quiz.getId(), quiz.getCourseId(), quiz.getTitle(), quiz.getKnowledgePoint(), quiz.getStatus(),
        quiz.getLearningSessionId(), quiz.getLearningRoundNumber(),
        questions.findByQuizId(quiz.getId()).stream()
            .map(question -> new QuizDtos.QuestionView(
                question.getId(), question.getContent(), fromJson(question.getOptionsJson()),
                question.getKnowledgePoint(), readSources(question.getSourceJson())))
            .toList());
  }

  private String sourceSnapshot(
      List<org.springframework.ai.document.Document> excerpts,
      List<WebSearchProvider.WebSearchResult> externalSources) {
    List<QuizDtos.SourceRef> sources = new ArrayList<>();
    for (var document : excerpts) {
      Object page = document.getMetadata().get("pageNumber");
      sources.add(new QuizDtos.SourceRef(
          "LOCAL", String.valueOf(document.getMetadata().getOrDefault("filename", "课程资料")), null,
          String.valueOf(document.getMetadata().getOrDefault("filename", "课程资料")),
          page instanceof Number number ? number.intValue() : null));
    }
    for (var source : externalSources) {
      sources.add(new QuizDtos.SourceRef("WEB", source.title(), source.url(), null, null));
    }
    return toJson(sources.stream().distinct().toList());
  }

  private List<QuizDtos.SourceRef> readSources(String value) {
    if (value == null || value.isBlank()) return List.of();
    try {
      return json.readValue(value, new TypeReference<>() { });
    } catch (JsonProcessingException exception) {
      return List.of();
    }
  }

  void validate(QuizDtos.GeneratedQuiz generated) {
    if (generated == null || generated.title() == null || generated.title().isBlank()
        || generated.questions() == null || generated.questions().isEmpty()) {
      throw invalid("invalid quiz structure");
    }
    for (int index = 0; index < generated.questions().size(); index++) {
      var question = generated.questions().get(index);
      if (question == null || question.content() == null || question.content().isBlank()) {
        throw invalid("question " + (index + 1) + " has no content");
      }
      if (question.options() == null || question.options().size() != 4) {
        throw invalid("question " + (index + 1) + " must contain exactly 4 unique options");
      }
      if (question.correctAnswer() == null || !question.options().contains(question.correctAnswer())) {
        throw invalid("question " + (index + 1) + " has a correctAnswer that does not match an option");
      }
      if (question.explanation() == null || question.explanation().isBlank()) {
        throw invalid("question " + (index + 1) + " has no explanation");
      }
      if (question.knowledgePoint() == null || question.knowledgePoint().isBlank()) {
        throw invalid("question " + (index + 1) + " has no knowledgePoint");
      }
    }
  }

  private ResponseStatusException invalid(String reason) {
    return new ResponseStatusException(
        HttpStatus.UNPROCESSABLE_ENTITY, "AI_GENERATION_FAILED: " + reason + ".");
  }

  private String toJson(Object values) {
    try {
      return json.writeValueAsString(values);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private List<String> fromJson(String value) {
    try {
      return json.readValue(value, new TypeReference<>() { });
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Stored question options are invalid.");
    }
  }

  public record WrongAnswer(
      UUID quizId,
      UUID questionId,
      String question,
      String userAnswer,
      String correctAnswer,
      String knowledgePoint) { }
}
