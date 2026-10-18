package com.learnagent.agent;
import java.util.UUID;
public interface AgentModelGateway { String respond(UUID courseId, String message, Object... tools); }
