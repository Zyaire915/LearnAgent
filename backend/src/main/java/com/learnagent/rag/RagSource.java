package com.learnagent.rag;

import java.util.UUID;

public record RagSource(UUID documentId, String filename, Integer pageNumber, String snippet, Double score) { }
