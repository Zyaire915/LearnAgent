package com.learnagent.rag;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

class LocalKnowledgeServiceTest {
  @Test void creates_a_distinct_course_metadata_filter_for_each_course() {
    LocalKnowledgeService service = new LocalKnowledgeService(new DefaultListableBeanFactory().getBeanProvider(org.springframework.ai.vectorstore.VectorStore.class), new DefaultListableBeanFactory().getBeanProvider(org.springframework.ai.chat.client.ChatClient.Builder.class), new RagProperties(5, 500, 350, 100), new SourceMapper());
    UUID courseA = UUID.randomUUID(); UUID courseB = UUID.randomUUID();
    assertThat(service.courseScopedSearchRequest(courseA, "permissions").getFilterExpression().toString()).contains(courseA.toString()).doesNotContain(courseB.toString());
    assertThat(service.courseScopedSearchRequest(courseB, "permissions").getFilterExpression().toString()).contains(courseB.toString()).doesNotContain(courseA.toString());
  }
}
