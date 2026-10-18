package com.learnagent.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.learnagent.analysis.AnalysisService;
import com.learnagent.skill.*;
import com.learnagent.task.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class AgentServiceTest {
  @Test void model_triggered_create_task_produces_success_step() {
    TaskService taskService=mock(TaskService.class);StudyTask saved=new StudyTask(null,"Java lab",null,null,TaskPriority.HIGH);when(taskService.create(any())).thenReturn(saved);AgentExecutionTracker tracker=new AgentExecutionTracker();TaskSkill taskSkill=new TaskSkill(taskService,tracker);
    AgentModelGateway gateway=(course,message,tools)->{Arrays.stream(tools).filter(TaskSkill.class::isInstance).map(TaskSkill.class::cast).findFirst().orElseThrow().createTask(new TaskSkill.CreateTaskInput(null,"Java lab",null,null,TaskPriority.HIGH));return "The task was created.";};
    AgentResponse response=service(gateway,tracker,taskSkill,mock(AnalysisSkill.class)).chat(null,"Create a Java lab task");
    verify(taskService).create(any());assertThat(response.answer()).contains("created");assertThat(response.steps()).anySatisfy(step->{assertThat(step.tool()).isEqualTo("createTask");assertThat(step.status()).isEqualTo(AgentStepStatus.SUCCESS);});
  }
  @Test void analysis_tool_returns_service_data() {
    AgentExecutionTracker tracker=new AgentExecutionTracker();AnalysisService analysisService=mock(AnalysisService.class);when(analysisService.weakPoints(null)).thenReturn(List.of(new AnalysisService.WeakPoint("Permissions",5,2,3,.4)));AnalysisSkill analysis=new AnalysisSkill(analysisService,tracker);
    AgentModelGateway gateway=(course,message,tools)->{analysis.getWeakPoints(null);return "Permissions is a weak point.";};AgentResponse response=service(gateway,tracker,mock(TaskSkill.class),analysis).chat(null,"Show weak points");
    verify(analysisService).weakPoints(null);assertThat(response.steps()).anyMatch(step->step.tool().equals("getWeakPoints")&&step.status()==AgentStepStatus.SUCCESS);
  }
  @Test void failed_tool_is_tracked_and_answer_never_claims_success() {
    TaskService taskService=mock(TaskService.class);when(taskService.create(any())).thenThrow(new IllegalStateException("database unavailable"));AgentExecutionTracker tracker=new AgentExecutionTracker();TaskSkill taskSkill=new TaskSkill(taskService,tracker);
    AgentModelGateway gateway=(course,message,tools)->{taskSkill.createTask(new TaskSkill.CreateTaskInput(null,"Fail",null,null,TaskPriority.MEDIUM));return "Created";};AgentResponse response=service(gateway,tracker,taskSkill,mock(AnalysisSkill.class)).chat(null,"Create task");
    assertThat(response.answer()).contains("failed").doesNotContain("Created");assertThat(response.steps()).anyMatch(step->step.tool().equals("createTask")&&step.status()==AgentStepStatus.FAILED);
  }
  @Test void includes_recent_conversation_history_for_follow_up_actions() {
    AgentExecutionTracker tracker=new AgentExecutionTracker();
    AgentModelGateway gateway=(course,message,tools)->{assertThat(message).contains("ASSISTANT: Proposed task: Review Java","Current user request:","Create it");return "done";};
    AgentResponse response=service(gateway,tracker,mock(TaskSkill.class),mock(AnalysisSkill.class)).chat(null,"Create it",List.of(new AgentConversationMessage("assistant","Proposed task: Review Java")));
    assertThat(response.answer()).isEqualTo("done");
  }
  private AgentService service(AgentModelGateway gateway,AgentExecutionTracker tracker,TaskSkill task,AnalysisSkill analysis){return new AgentService(gateway,tracker,mock(KnowledgeSkill.class),mock(WebSearchSkill.class),task,mock(QuizSkill.class),analysis,mock(PlanningSkill.class),mock(WeeklyReviewSkill.class),mock(AdaptiveLearningSkill.class),mock(WorkspaceSkill.class),mock(MemorySkill.class));}
}
