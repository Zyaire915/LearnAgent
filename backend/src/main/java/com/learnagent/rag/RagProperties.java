package com.learnagent.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "learnagent.rag")
public record RagProperties(int topK, int chunkSize, int minChunkSizeChars, int maxChunksPerPage) { }
