package com.learnagent.rag;

import java.util.List;
public record RagAnswer(String answer, List<RagSource> sources) { }
