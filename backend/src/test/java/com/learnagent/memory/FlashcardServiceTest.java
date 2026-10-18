package com.learnagent.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnagent.course.CourseService;
import com.learnagent.quiz.*;
import com.learnagent.rag.LocalKnowledgeService;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

class FlashcardServiceTest {
  @Test void repeatedWrongAnswerCreatesOnlyOneCandidate() {
    FlashcardRepository cards = mock(FlashcardRepository.class);
    QuizRepository quizzes = mock(QuizRepository.class);
    QuestionRepository questions = mock(QuestionRepository.class);
    UUID courseId=UUID.randomUUID(),quizId=UUID.randomUUID();
    Quiz quiz=new Quiz(courseId,"Quiz","Permissions");
    Question question=new Question(quizId,"What does chmod do?","[\"A\",\"B\",\"C\",\"D\"]","A","It changes modes.","Permissions","[]");
    when(quizzes.findById(quizId)).thenReturn(Optional.of(quiz));
    when(questions.findByQuizId(quizId)).thenReturn(List.of(question));
    AtomicReference<Flashcard> saved=new AtomicReference<>();
    when(cards.findByCourseIdAndContentHash(eq(courseId),any())).thenAnswer(call->Optional.ofNullable(saved.get()));
    when(cards.save(any())).thenAnswer(call->{Flashcard value=call.getArgument(0);saved.set(value);return value;});
    ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    FsrsSchedulerService fsrs=new FsrsSchedulerService(mapper);
    FlashcardService service=new FlashcardService(cards,mock(FlashcardReviewStateRepository.class),quizzes,questions,mock(CourseService.class),mock(LocalKnowledgeService.class),new DefaultListableBeanFactory().getBeanProvider(org.springframework.ai.chat.client.ChatClient.Builder.class),fsrs,mock(KnowledgeGraphService.class),mapper,Clock.systemUTC());
    QuizDtos.Result wrong=new QuizDtos.Result(question.getId(),false,"A","It changes modes.","Permissions");
    QuizDtos.Submission submission=new QuizDtos.Submission(1,0,0,List.of(wrong));
    assertThat(service.fromSubmission(quizId,submission)).isEqualTo(1);
    assertThat(saved.get().getSourceJson()).contains("QUIZ", "测验错题");
    assertThat(service.fromSubmission(quizId,submission)).isZero();
    verify(cards,times(1)).save(any());
  }

  @Test void sourceLessCandidateCannotBeApproved() {
    FlashcardRepository cards = mock(FlashcardRepository.class);
    FlashcardReviewStateRepository states = mock(FlashcardReviewStateRepository.class);
    Flashcard card = new Flashcard(UUID.randomUUID(), null, null, null, FlashcardType.BASIC,
        "Question", "Answer", "Point", "[]", "hash");
    when(cards.findById(card.getId())).thenReturn(Optional.of(card));
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    FlashcardService service = new FlashcardService(cards, states, mock(QuizRepository.class),
        mock(QuestionRepository.class), mock(CourseService.class), mock(LocalKnowledgeService.class),
        new DefaultListableBeanFactory().getBeanProvider(org.springframework.ai.chat.client.ChatClient.Builder.class),
        new FsrsSchedulerService(mapper), mock(KnowledgeGraphService.class), mapper, Clock.systemUTC());

    assertThatThrownBy(() -> service.approve(card.getId()))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
        .hasMessageContaining("traceable source");
    verifyNoInteractions(states);
  }
}
