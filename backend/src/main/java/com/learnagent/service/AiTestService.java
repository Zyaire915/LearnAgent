package com.learnagent.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AiTestService {
  private final ObjectProvider<ChatClient.Builder> chatClientBuilder;
  private final boolean providerKeyConfigured;
  private final boolean chatProviderEnabled;
  private final String providerName;
  private final String keyEnvironmentName;
  public AiTestService(ObjectProvider<ChatClient.Builder> chatClientBuilder,
      @Value("${spring.ai.openai.api-key:}") String apiKey,
      @Value("${spring.ai.model.chat:none}") String chatProvider,
      @Value("${learnagent.ai.provider-name:OpenAI}") String providerName,
      @Value("${learnagent.ai.key-env-name:OPENAI_API_KEY}") String keyEnvironmentName) {
    this.chatClientBuilder = chatClientBuilder;
    this.providerKeyConfigured = apiKey != null && !apiKey.isBlank();
    this.chatProviderEnabled = "openai".equalsIgnoreCase(chatProvider);
    this.providerName = providerName;
    this.keyEnvironmentName = keyEnvironmentName;
  }
  public AiTestResult test() {
    if (!providerKeyConfigured) return new AiTestResult(false, null,
        providerName + " is not configured. Set " + keyEnvironmentName
            + " in your environment or .env before calling this endpoint.");
    if (!chatProviderEnabled) return new AiTestResult(false, null,
        "The LLM provider is disabled. Enable the selected AI profile and restart the backend.");
    ChatClient.Builder builder = chatClientBuilder.getIfAvailable();
    if (builder == null) return new AiTestResult(false, null,
        "No Spring AI chat client is available. Check the configured model provider.");
    try {
      String answer = builder.build().prompt().user("Reply with a short greeting from LearnAgent.").call().content();
      return new AiTestResult(true, answer, null);
    } catch (Exception exception) {
      return new AiTestResult(false, null, "LLM request failed: " + rootMessage(exception));
    }
  }
  private String rootMessage(Exception exception) {
    Throwable current = exception;
    while (current.getCause() != null) current = current.getCause();
    return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
  }
  public record AiTestResult(boolean success, String answer, String message) { }
}
