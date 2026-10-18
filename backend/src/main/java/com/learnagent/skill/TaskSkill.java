package com.learnagent.skill;

import com.learnagent.agent.AgentExecutionTracker;
import com.learnagent.task.*;
import java.time.Instant;
import java.util.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class TaskSkill {
  private final TaskService service; private final AgentExecutionTracker tracker;
  public TaskSkill(TaskService service, AgentExecutionTracker tracker){this.service=service;this.tracker=tracker;}
  @Tool(description="Create a real study task in the database. Use only when the user asks to create or schedule work.")
  public TaskResult createTask(@ToolParam(description="Task creation fields including optional courseId and ISO-8601 dueAt") CreateTaskInput input){return tracker.execute("Task Skill","createTask",()->result(service.create(new TaskDtos.Request(input.courseId(),input.title(),input.description(),parse(input.dueAt()),TaskStatus.TODO,input.priority()==null?TaskPriority.MEDIUM:input.priority()))),value->"Created task: "+value.title());}
  @Tool(description="Query real study tasks, optionally filtered by course and status.")
  public List<TaskResult> queryTasks(@ToolParam(description="Optional task filters") QueryTasksInput input){return tracker.execute("Task Skill","queryTasks",()->service.list(input==null?null:input.courseId(),input==null?null:input.status()).stream().map(this::result).toList(),values->"Found "+values.size()+" tasks");}
  @Tool(description="Mark an existing study task completed in the database.")
  public TaskResult completeTask(@ToolParam(description="ID of the task to complete") UUID taskId){return tracker.execute("Task Skill","completeTask",()->result(service.complete(taskId)),value->"Completed task: "+value.title());}
  @Tool(description="Update an existing study task. Provide its current courseId and desired fields.")
  public TaskResult updateTask(@ToolParam(description="Task update fields") UpdateTaskInput input){return tracker.execute("Task Skill","updateTask",()->result(service.update(input.taskId(),new TaskDtos.Request(input.courseId(),input.title(),input.description(),parse(input.dueAt()),input.status(),input.priority()))),value->"Updated task: "+value.title());}
  private Instant parse(String value){return value==null||value.isBlank()?null:Instant.parse(value);} private TaskResult result(StudyTask task){return new TaskResult(task.getId(),task.getCourseId(),task.getTitle(),task.getDescription(),task.getDueAt(),task.getStatus(),task.getPriority(),task.getCompletedAt());}
  public record CreateTaskInput(UUID courseId,String title,String description,String dueAt,TaskPriority priority){} public record QueryTasksInput(UUID courseId,TaskStatus status){} public record UpdateTaskInput(UUID taskId,UUID courseId,String title,String description,String dueAt,TaskStatus status,TaskPriority priority){} public record TaskResult(UUID id,UUID courseId,String title,String description,Instant dueAt,TaskStatus status,TaskPriority priority,Instant completedAt){}
}
