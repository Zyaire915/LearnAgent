package com.learnagent.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnagent.quiz.AnswerRecordRepository;
import java.util.*;
import org.junit.jupiter.api.Test;

class KnowledgeGraphServiceTest {
  @Test void sourceLessRelationIsRejectedButTraceableRelationIsStored() {
    KnowledgeNodeRepository nodes = mock(KnowledgeNodeRepository.class);
    KnowledgeEdgeRepository edges = mock(KnowledgeEdgeRepository.class);
    UUID courseId = UUID.randomUUID();
    when(nodes.findByCourseIdAndNormalizedKey(eq(courseId), any())).thenReturn(Optional.empty());
    when(nodes.save(any())).thenAnswer(call -> call.getArgument(0));
    KnowledgeGraphService service = new KnowledgeGraphService(nodes, edges, mock(FlashcardRepository.class),
        mock(FlashcardReviewStateRepository.class), mock(AnswerRecordRepository.class),
        mock(FsrsSchedulerService.class), new ObjectMapper().findAndRegisterModules());

    service.addEdge(courseId, "JVM", "Java", KnowledgeRelation.PART_OF, "[]");
    verifyNoInteractions(nodes, edges);

    String source = "[{\"type\":\"LOCAL\",\"filename\":\"notes.pdf\",\"pageNumber\":2}]";
    service.addEdge(courseId, "Java", "JVM", KnowledgeRelation.RELATED, source);
    verify(edges).save(any(KnowledgeEdge.class));
  }

  @Test void nodeRiskIsOneMinusFsrsRetrievability() {
    KnowledgeNode node = new KnowledgeNode(UUID.randomUUID(), "JVM", "jvm", "[]");
    node.update(.75, .40, "[]");
    assertThat(node.getMastery()).isEqualTo(.75);
    assertThat(node.getRetrievability()).isEqualTo(.40);
    assertThat(node.getForgettingRisk()).isEqualTo(.60);
  }
}
