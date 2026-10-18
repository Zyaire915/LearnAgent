package com.learnagent.agent;
public record AgentStep(String skill, String tool, AgentStepStatus status, String summary) { }
