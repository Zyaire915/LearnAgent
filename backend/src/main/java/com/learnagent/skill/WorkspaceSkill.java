package com.learnagent.skill;

import com.learnagent.agent.AgentExecutionTracker;
import com.learnagent.workspace.*;
import java.util.UUID;
import org.springframework.ai.tool.annotation.*;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceSkill {
  private final WorkspaceService service;
  private final AgentExecutionTracker tracker;
  public WorkspaceSkill(WorkspaceService service, AgentExecutionTracker tracker) {
    this.service = service; this.tracker = tracker;
  }

  @Tool(description = "Create a learning project from the user's free-form description. Generate a safe workspace, project plan, linked tasks, cited research notes, and suitable starter files. Use the currently selected course ID when one is supplied.")
  public WorkspaceResult createProjectWorkspace(
      @ToolParam(description = "Optional related course ID", required = false) UUID courseId,
      @ToolParam(description = "Optional project display name; leave blank to derive it from the description", required = false) String name,
      @ToolParam(description = "Detailed project goal, desired features or deliverable, and any constraints") String description) {
    return tracker.execute("Workspace Skill", "createProjectWorkspace", () -> {
      var created = service.create(new WorkspaceService.CreateRequest(courseId, name, description, WorkspaceTemplate.GENERIC));
      return result(service.generate(created.id()));
    }, value -> "Generated learning project: " + value.name());
  }

  @Tool(description = "Open an existing LearnAgent project's verified D-drive folder in Windows File Explorer.")
  public OpenFolderResult openProjectFolder(@ToolParam(description = "Workspace ID") UUID workspaceId) {
    return tracker.execute("Workspace Skill", "openProjectFolder", () -> {
      var opened = service.openFolder(workspaceId);
      return new OpenFolderResult(opened.path(), opened.opened());
    }, value -> value.opened() ? "Opened project folder" : "Project folder was not opened");
  }

  private WorkspaceResult result(WorkspaceService.ProjectView value) {
    return new WorkspaceResult(value.id(), value.name(), value.status().name(), value.rootPath(),
        "/workbench?workspaceId=" + value.id());
  }
  public record WorkspaceResult(UUID id, String name, String status, String rootPath, String actionUrl) { }
  public record OpenFolderResult(String path, boolean opened) { }
}
