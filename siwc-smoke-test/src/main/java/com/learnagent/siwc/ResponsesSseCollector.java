package com.learnagent.siwc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;

final class ResponsesSseCollector {
  private final ObjectMapper json;
  private final StringBuilder eventData = new StringBuilder();
  private final StringBuilder text = new StringBuilder();
  private boolean completed;
  private String failure;

  ResponsesSseCollector(ObjectMapper json) {
    this.json = json;
  }

  Result collect(BufferedReader reader) throws IOException {
    String line;
    while ((line = reader.readLine()) != null) {
      acceptLine(line);
    }
    dispatchEvent();
    if (failure != null) {
      throw new IllegalStateException("Responses API failed: " + failure);
    }
    if (!completed) {
      throw new IllegalStateException("The Responses stream ended without response.completed.");
    }
    return new Result(text.toString(), true);
  }

  void acceptLine(String line) throws IOException {
    if (line.isEmpty()) {
      dispatchEvent();
      return;
    }
    if (line.startsWith("data:")) {
      if (!eventData.isEmpty()) {
        eventData.append('\n');
      }
      eventData.append(line.substring(5).stripLeading());
    }
  }

  private void dispatchEvent() throws IOException {
    if (eventData.isEmpty()) {
      return;
    }
    String payload = eventData.toString();
    eventData.setLength(0);
    if ("[DONE]".equals(payload)) {
      return;
    }

    JsonNode event = json.readTree(payload);
    String type = event.path("type").asText();
    switch (type) {
      case "response.output_text.delta" -> text.append(event.path("delta").asText());
      case "response.completed" -> completed = true;
      case "response.failed" -> failure = describeError(event.path("response").path("error"));
      case "response.incomplete" -> failure = "response.incomplete";
      case "error" -> failure = describeError(event.path("error"));
      default -> {
        // Other lifecycle events do not change the final smoke-test result.
      }
    }
  }

  private static String describeError(JsonNode error) {
    if (error.isMissingNode() || error.isNull()) {
      return "unknown_error";
    }
    String code = error.path("code").asText("unknown_error");
    String message = error.path("message").asText("");
    return message.isBlank() ? code : code + ": " + message;
  }

  record Result(String text, boolean completed) {}
}
