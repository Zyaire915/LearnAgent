package com.learnagent.service;

import com.learnagent.repository.HealthProbeRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SystemStatusService {
  private final HealthProbeRepository healthProbeRepository;
  private final ObjectProvider<ChatClient.Builder> chat;
  private final ObjectProvider<EmbeddingModel> embeddings;
  private final ObjectProvider<VectorStore> vectors;
  private final boolean webSearchConfigured;private final boolean schedulerEnabled;
  public SystemStatusService(HealthProbeRepository healthProbeRepository,
      ObjectProvider<ChatClient.Builder> chat,ObjectProvider<EmbeddingModel> embeddings,ObjectProvider<VectorStore> vectors,
      @Value("${learnagent.web-search.api-key:}")String webSearchKey,@Value("${learnagent.weekly-review.enabled:true}")boolean schedulerEnabled) {
    this.healthProbeRepository = healthProbeRepository;
    this.chat=chat;this.embeddings=embeddings;this.vectors=vectors;this.webSearchConfigured=webSearchKey!=null&&!webSearchKey.isBlank();this.schedulerEnabled=schedulerEnabled;
  }
  public Status status() { boolean chatReady=available(chat)!=null;boolean embeddingReady=available(embeddings)!=null;boolean vectorReady=available(vectors)!=null;return new Status(healthProbeRepository.canConnect(),chatReady,chatReady?"CONFIGURED":"NOT_CONFIGURED",embeddingReady?"CONFIGURED":"NOT_CONFIGURED",vectorReady?"READY":"NOT_CONFIGURED",webSearchConfigured?"CONFIGURED":"NOT_CONFIGURED",schedulerEnabled?"ENABLED":"DISABLED"); }
  private <T>T available(ObjectProvider<T> provider){try{return provider.getIfAvailable();}catch(BeansException exception){return null;}}
  public record Status(boolean databaseConnected,boolean llmConfigured,String chatProvider,String embeddingProvider,String vectorStore,String webSearch,String scheduler) { }
}
