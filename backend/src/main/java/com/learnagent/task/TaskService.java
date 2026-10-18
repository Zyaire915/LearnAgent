package com.learnagent.task;

import com.learnagent.course.CourseService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TaskService {
  private final StudyTaskRepository tasks;
  private final CourseService courses;

  public TaskService(StudyTaskRepository tasks, CourseService courses) {
    this.tasks = tasks;
    this.courses = courses;
  }

  @Transactional
  public StudyTask create(TaskDtos.Request request) {
    verifyCourse(request.courseId());
    String title = name(request.title());
    Optional<StudyTask> existing = tasks.findFirstByTitleIgnoreCaseAndCourseId(title, request.courseId())
        .filter(task -> task.getStatus() != TaskStatus.COMPLETED);
    if (existing.isPresent()) {
      return existing.get();
    }
    validateDueAt(request.dueAt());
    return tasks.save(new StudyTask(
        request.courseId(), title, clean(request.description()), request.dueAt(), request.priority()));
  }

  @Transactional(readOnly = true)
  public List<StudyTask> list(UUID courseId, TaskStatus status) {
    if (courseId != null && status != null) return tasks.findByCourseIdAndStatus(courseId, status);
    if (courseId != null) return tasks.findByCourseId(courseId);
    if (status != null) return tasks.findByStatus(status);
    return tasks.findAll();
  }

  @Transactional(readOnly = true)
  public StudyTask get(UUID id) {
    return tasks.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found."));
  }

  @Transactional
  public StudyTask update(UUID id, TaskDtos.Request request) {
    StudyTask task = get(id);
    verifyCourse(request.courseId());
    validateDueAtForUpdate(task, request.dueAt());
    task.updateCourse(request.courseId());
    task.update(
        name(request.title()), clean(request.description()), request.dueAt(), request.status(), request.priority());
    return task;
  }

  @Transactional
  public StudyTask complete(UUID id) {
    StudyTask task = get(id);
    task.complete();
    return task;
  }

  @Transactional
  public void delete(UUID id) {
    tasks.delete(get(id));
  }

  private void verifyCourse(UUID id) {
    if (id != null) courses.get(id);
  }

  private void validateDueAt(Instant dueAt) {
    if (dueAt != null && dueAt.isBefore(Instant.now())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "TASK_DUE_DATE_IN_PAST: choose a due date later than the current time.");
    }
  }

  private void validateDueAtForUpdate(StudyTask task, Instant dueAt) {
    if (dueAt != null && dueAt.isBefore(Instant.now())
        && (task.getDueAt() == null || !dueAt.equals(task.getDueAt()))) {
      validateDueAt(dueAt);
    }
  }

  private String name(String value) {
    if (value == null || value.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task title is required.");
    }
    return value.trim();
  }

  private String clean(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
