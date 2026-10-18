package com.learnagent.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.learnagent.document.CourseDocumentRepository;
import com.learnagent.rag.LocalKnowledgeService;
import org.junit.jupiter.api.Test;

class CourseServiceTest {
  @Test void creates_a_course_with_trimmed_name() {
    CourseRepository courses = mock(CourseRepository.class); when(courses.save(any(Course.class))).thenAnswer(invocation -> invocation.getArgument(0));
    CourseService service = new CourseService(courses, mock(CourseDocumentRepository.class), mock(LocalKnowledgeService.class));
    Course course = service.create("  Operating Systems  ", "kernel concepts");
    assertThat(course.getName()).isEqualTo("Operating Systems"); verify(courses).save(course);
  }
}
