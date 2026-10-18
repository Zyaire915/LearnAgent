package com.learnagent.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.*;

public final class ChatDtos {
  private ChatDtos() { }
  public record ChatRequest(@NotNull UUID courseId, @NotBlank String message) { }
  public record SourceResponse(UUID documentId, String filename, Integer pageNumber, String snippet, Double score) { }
  public record ChatResponse(String answer, List<SourceResponse> sources) { }
}
