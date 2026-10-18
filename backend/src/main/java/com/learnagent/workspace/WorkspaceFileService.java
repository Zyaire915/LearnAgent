package com.learnagent.workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkspaceFileService {
  private static final int MAX_TEXT_CHARS = 1_000_000;
  private final WorkspacePathPolicy paths;
  public WorkspaceFileService(WorkspacePathPolicy paths) { this.paths = paths; }
  public Path write(String relative, String content) {
    if (content == null || content.length() > MAX_TEXT_CHARS) {
      throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Workspace text files are limited to 1 MB.");
    }
    Path path = paths.resolveFile(relative);
    try {
      Files.createDirectories(path.getParent());
      Files.writeString(path, content, StandardCharsets.UTF_8);
      return path;
    } catch (IOException exception) {
      throw new IllegalStateException("Could not write workspace file.", exception);
    }
  }
  public String read(String relative) {
    Path path = paths.resolveFile(relative);
    try {
      if (!Files.isRegularFile(path) || Files.size(path) > MAX_TEXT_CHARS) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace file not found or too large.");
      }
      return Files.readString(path, StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new IllegalStateException("Could not read workspace file.", exception);
    }
  }
}
