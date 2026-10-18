package com.learnagent.workspace;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class WorkspacePathPolicy {
  private static final Set<String> ALLOWED = Set.of(
      ".md", ".txt", ".java", ".xml", ".yml", ".yaml", ".json", ".properties", ".gitignore",
      ".py", ".js", ".ts", ".vue", ".html", ".css", ".sql", ".csv", ".sh", ".ps1");
  private final Path root;
  public WorkspacePathPolicy(@Value("${learnagent.workspace.root:workspaces}") String root) {
    this.root = Path.of(root).toAbsolutePath().normalize();
  }
  @PostConstruct void initialize() throws IOException { Files.createDirectories(root); }
  public Path root() { return root; }
  public Path resolveDirectory(String relative) { return resolve(relative, false); }
  public Path resolveFile(String relative) {
    Path path = resolve(relative, true);
    String name = path.getFileName().toString();
    String extension = name.equals(".gitignore") ? ".gitignore" : name.contains(".")
        ? name.substring(name.lastIndexOf('.')).toLowerCase() : "";
    if (!ALLOWED.contains(extension)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported workspace file type: " + extension);
    }
    return path;
  }
  private Path resolve(String relative, boolean file) {
    if (relative == null || relative.isBlank() || Path.of(relative).isAbsolute()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A relative workspace path is required.");
    }
    Path candidate = root.resolve(relative.replace('\\', '/')).normalize();
    if (!candidate.startsWith(root)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workspace path escapes the D-drive sandbox.");
    }
    Path current = root;
    Path relativePath = root.relativize(candidate);
    for (Path part : relativePath) {
      current = current.resolve(part);
      if (Files.exists(current) && Files.isSymbolicLink(current)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Symbolic links are not allowed in the workspace.");
      }
    }
    if (file && candidate.equals(root)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A file path is required.");
    }
    return candidate;
  }
}
