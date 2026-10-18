package com.learnagent.skill;

import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.BeansException;
import org.springframework.stereotype.Service;
import com.learnagent.websearch.WebSearchProvider;

@Service
public class SkillStatusService {
  private final ObjectProvider<ChatClient.Builder> chat; private final ObjectProvider<VectorStore> vectors;private final WebSearchProvider web;
  public SkillStatusService(ObjectProvider<ChatClient.Builder> chat,ObjectProvider<VectorStore> vectors,WebSearchProvider web){this.chat=chat;this.vectors=vectors;this.web=web;}
  public List<SkillStatus> statuses(){boolean ai=available(chat)!=null;boolean vector=available(vectors)!=null;return List.of(
      new SkillStatus("knowledge","Knowledge Search","Search course PDF knowledge through Local RAG",vector,vector?Availability.AVAILABLE:Availability.UNAVAILABLE,vector?"Ready":"Vector store provider required"),
      new SkillStatus("task","Task Manager","Create, query, update, and complete real study tasks",true,Availability.AVAILABLE,"Ready"),
      new SkillStatus("quiz","Quiz Generator","Generate quizzes and inspect wrong answers",ai&&vector,ai&&vector?Availability.AVAILABLE:Availability.DEGRADED,ai&&vector?"Ready":"AI and vector providers required for generation; history remains available"),
      new SkillStatus("adaptive-test","Adaptive Test","Run persistent mastery-based test rounds",ai&&vector,ai&&vector?Availability.AVAILABLE:Availability.DEGRADED,ai&&vector?"Five-question adaptive rounds ready":"AI and vector providers required"),
      new SkillStatus("workspace","Project Workspace","Generate project plans, tasks, research and starter files from a free-form description",true,Availability.AVAILABLE,"D-drive sandbox ready"),
      new SkillStatus("memory","Memory Reinforcement","FSRS spaced repetition and knowledge graph",true,Availability.AVAILABLE,"90% target retention; local scheduling ready"),
      new SkillStatus("analysis","Learning Analysis","Read weak points and learning statistics",true,Availability.AVAILABLE,"Ready"),
      new SkillStatus("planning","Study Planner","Generate structured study plans from goals and weak points",ai,ai?Availability.AVAILABLE:Availability.UNAVAILABLE,ai?"Ready":"AI provider required")
      ,new SkillStatus("web-search","Web Search","Search current public external sources",web.isConfigured(),web.isConfigured()?Availability.AVAILABLE:Availability.UNAVAILABLE,web.isConfigured()?web.providerName()+" ready":"TAVILY_API_KEY required")
      ,new SkillStatus("weekly-review","Weekly Review","Generate proactive reviews from real learning statistics",true,ai?Availability.AVAILABLE:Availability.DEGRADED,ai?"Statistics and AI summary ready":"Statistics ready; AI summary unavailable")
    );}
  private <T>T available(ObjectProvider<T> provider){try{return provider.getIfAvailable();}catch(BeansException exception){return null;}}
  public enum Availability{AVAILABLE,DEGRADED,UNAVAILABLE}
  public record SkillStatus(String id,String name,String description,boolean enabled,Availability status,String detail){}
}
