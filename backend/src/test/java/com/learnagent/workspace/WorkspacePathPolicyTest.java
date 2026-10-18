package com.learnagent.workspace;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class WorkspacePathPolicyTest {
  private WorkspacePathPolicy policy() throws Exception {
    WorkspacePathPolicy value = new WorkspacePathPolicy("D:/LearnAgent/.tmp/workspace-policy-test");
    value.initialize();
    return value;
  }
  @Test void rejectsTraversalOutsideSandbox() throws Exception {
    assertThatThrownBy(() -> policy().resolveFile("../secret.md")).isInstanceOf(ResponseStatusException.class);
  }
  @Test void rejectsExecutableFiles() throws Exception {
    assertThatThrownBy(() -> policy().resolveFile("demo/run.exe")).isInstanceOf(ResponseStatusException.class);
  }
  @Test void acceptsMarkdownInsideSandbox() throws Exception {
    Path value = policy().resolveFile("demo/README.md");
    org.assertj.core.api.Assertions.assertThat(value.toString()).startsWith(Path.of("D:/LearnAgent/.tmp/workspace-policy-test").toAbsolutePath().normalize().toString());
  }
}
