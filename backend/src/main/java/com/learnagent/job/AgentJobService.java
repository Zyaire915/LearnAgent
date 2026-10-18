package com.learnagent.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AgentJobService {
  private final AgentJobRepository jobs;
  private final ObjectMapper json;
  private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
  public AgentJobService(AgentJobRepository jobs, ObjectMapper json) { this.jobs = jobs; this.json = json; }

  public <T> T run(String type, String dedup, Object input, Function<JobContext, T> work) {
    if (dedup != null) {
      AgentJob existing = jobs.findByDedupKey(dedup).orElse(null);
      if (existing != null && existing.getStatus() != AgentJobStatus.FAILED) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "A job with this idempotency key already exists.");
      }
      if (existing != null) { existing.clearDedupKey(); jobs.saveAndFlush(existing); }
    }
    AgentJob job = jobs.saveAndFlush(new AgentJob(type, write(input), dedup));
    job.start(); jobs.saveAndFlush(job); publish(job);
    try {
      T result = work.apply(new JobContext(job));
      job.succeed(write(result)); jobs.saveAndFlush(job); publish(job);
      return result;
    } catch (RuntimeException exception) {
      job.fail(root(exception)); jobs.saveAndFlush(job); publish(job);
      throw exception;
    }
  }

  @Transactional(readOnly = true)
  public AgentJob get(UUID id) {
    return jobs.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found."));
  }
  @Transactional(readOnly = true) public List<AgentJob> list() { return jobs.findAllByOrderByCreatedAtDesc(); }
  public SseEmitter subscribe() {
    SseEmitter emitter = new SseEmitter(0L); emitters.add(emitter);
    emitter.onCompletion(() -> emitters.remove(emitter)); emitter.onTimeout(() -> emitters.remove(emitter));
    return emitter;
  }
  private void publish(AgentJob job) {
    for (SseEmitter emitter : emitters) {
      try { emitter.send(SseEmitter.event().name("job").data(Map.of(
          "id", job.getId(), "type", job.getJobType(), "status", job.getStatus(), "progress", job.getProgress()))); }
      catch (IOException exception) { emitter.complete(); emitters.remove(emitter); }
    }
  }
  private String write(Object value) {
    try { return json.writeValueAsString(value); }
    catch (Exception exception) { throw new IllegalStateException("Could not serialize job data.", exception); }
  }
  private String root(Throwable error) {
    Throwable value = error; while (value.getCause() != null) value = value.getCause();
    return value.getMessage() == null ? value.getClass().getSimpleName() : value.getMessage();
  }
  public final class JobContext {
    private final AgentJob job;
    private JobContext(AgentJob job) { this.job = job; }
    public UUID id() { return job.getId(); }
    public void progress(int value, String line) { job.progress(value, line); jobs.saveAndFlush(job); publish(job); }
  }
}
