package com.learnagent.planning;

import com.learnagent.rag.AiFeatureUnavailableException;
import java.time.Instant;
import java.util.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.BeansException;
import org.springframework.stereotype.Service;

@Service
public class PlanningService {
  private final ObjectProvider<ChatClient.Builder> builders;
  public PlanningService(ObjectProvider<ChatClient.Builder> builders){this.builders=builders;}
  public StudyPlan generate(UUID courseId,String goal,List<String> weakPoints,Instant deadline){
    ChatClient.Builder builder=availableBuilder();if(builder==null)throw new AiFeatureUnavailableException("AI_PROVIDER_NOT_CONFIGURED: study planning requires a chat provider.");
    String prompt="Current time: "+Instant.now()+"\nCourseId: "+courseId+"\nGoal: "+goal+"\nWeak points: "+weakPoints+"\nDeadline: "+deadline+"\nCreate a realistic short study plan. Return scheduledAt as ISO-8601 UTC.";
    StudyPlan plan=builder.build().prompt().system("Create concise study plans from the supplied goal and weak points. Never schedule an item in the past unless the user explicitly requests it. Do not claim tasks were created; task creation requires the separate createTask tool.").user(prompt).call().entity(StudyPlan.class);
    if(plan==null||plan.items()==null||plan.items().isEmpty())throw new IllegalStateException("AI_GENERATION_FAILED: planning model returned an empty plan.");return new StudyPlan(courseId,goal,List.copyOf(plan.items()));
  }
  private ChatClient.Builder availableBuilder(){try{return builders.getIfAvailable();}catch(BeansException exception){return null;}}
  public record StudyPlan(UUID courseId,String goal,List<StudyPlanItem> items){}
  public record StudyPlanItem(String topic,Instant scheduledAt,int estimatedMinutes,String objective){}
}
