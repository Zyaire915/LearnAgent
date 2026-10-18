package com.learnagent.rag;

import java.util.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class LocalKnowledgeService {
  private final ObjectProvider<VectorStore> vectorStore; private final ObjectProvider<ChatClient.Builder> chatClient;
  private final RagProperties properties; private final SourceMapper sourceMapper;
  public LocalKnowledgeService(ObjectProvider<VectorStore> vectorStore, ObjectProvider<ChatClient.Builder> chatClient, RagProperties properties, SourceMapper sourceMapper) { this.vectorStore = vectorStore; this.chatClient = chatClient; this.properties = properties; this.sourceMapper = sourceMapper; }
  public void ingestDocument(UUID courseId, UUID documentId, String filename, List<PageText> pages) {
    VectorStore store = requiredVectorStore(); List<Document> chunks = new ArrayList<>();
    TokenTextSplitter splitter = TokenTextSplitter.builder().withChunkSize(properties.chunkSize()).withMinChunkSizeChars(properties.minChunkSizeChars()).withMinChunkLengthToEmbed(Math.min(20, properties.minChunkSizeChars())).withMaxNumChunks(properties.maxChunksPerPage()).withKeepSeparator(true).build();
    for (PageText page : pages) {
      if (page.text() == null || page.text().isBlank()) continue;
      Map<String, Object> metadata = new HashMap<>(); metadata.put("courseId", courseId.toString()); metadata.put("documentId", documentId.toString()); metadata.put("filename", filename); metadata.put("sourceType", "LOCAL"); metadata.put("pageNumber", page.pageNumber());
      chunks.addAll(splitter.split(new Document(page.text(), metadata)));
    }
    if (chunks.isEmpty()) throw new IllegalArgumentException("The PDF contains no extractable text.");
    store.add(chunks);
  }
  public List<Document> search(UUID courseId, String query) {
    if (query == null || query.isBlank()) return List.of();
    return requiredVectorStore().similaritySearch(courseScopedSearchRequest(courseId, query));
  }
  SearchRequest courseScopedSearchRequest(UUID courseId, String query) { return SearchRequest.builder().query(query.trim()).topK(properties.topK()).similarityThresholdAll().filterExpression("courseId == '" + courseId + "'").build(); }
  public RagAnswer answer(UUID courseId, String question) {
    List<Document> retrieved = search(courseId, question); List<RagSource> sources = sourceMapper.mapAndDeduplicate(retrieved);
    if (retrieved.isEmpty()) return new RagAnswer("课程资料中没有足够信息来回答这个问题。请上传相关资料或换一种问法。", sources);
    ChatClient.Builder builder = chatClient.getIfAvailable();
    if (builder == null) throw new AiFeatureUnavailableException("RAG chat is not configured. Configure an AI key, enable the chat and embedding providers plus PGVector, then restart the backend.");
    String context = retrieved.stream().map(Document::getText).reduce((left, right) -> left + "\n\n---\n\n" + right).orElse("");
    String answer = builder.build().prompt().system("You are LearnAgent. Answer only from the supplied course excerpts. If the excerpts are insufficient, say so plainly. Do not invent page numbers or claim a source says something it does not.").user("Course excerpts:\n" + context + "\n\nStudent question: " + question).call().content();
    return new RagAnswer(answer, sources);
  }
  public void deleteDocumentVectors(UUID documentId) {
    VectorStore store = vectorStore.getIfAvailable(); if (store != null) store.delete(new FilterExpressionTextParser().parse("documentId == '" + documentId + "'"));
  }
  private VectorStore requiredVectorStore() { VectorStore store = vectorStore.getIfAvailable(); if (store == null) throw new AiFeatureUnavailableException("Document processing is not configured. Configure an embedding provider and PGVector, then restart the backend."); return store; }
  public record PageText(int pageNumber, String text) { }
}
