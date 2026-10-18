package com.learnagent.agent;
import com.learnagent.knowledge.KnowledgeSource;
import java.util.List;
public record AgentResponse(String answer, List<AgentStep> steps, List<KnowledgeSource> sources) { }
