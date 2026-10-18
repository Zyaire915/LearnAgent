package com.learnagent.skill;

import static org.assertj.core.api.Assertions.assertThat;
import com.learnagent.analysis.AnalysisService;
import com.learnagent.course.*;
import com.learnagent.quiz.*;
import com.learnagent.task.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named="RUN_DB_INTEGRATION",matches="true")
class SkillDatabaseIntegrationTest {
  @Autowired CourseRepository courses; @Autowired StudyTaskRepository tasks; @Autowired QuizRepository quizzes; @Autowired QuestionRepository questions; @Autowired AnswerRecordRepository answers; @Autowired TaskSkill taskSkill; @Autowired AnalysisSkill analysisSkill;
  @Test void tools_write_and_read_real_postgresql_data(){Course course=courses.save(new Course("Tool Integration","Real database tool path"));TaskSkill.TaskResult created=taskSkill.createTask(new TaskSkill.CreateTaskInput(course.getId(),"Review permissions",null,null,TaskPriority.HIGH));assertThat(tasks.findById(created.id())).isPresent();taskSkill.completeTask(created.id());assertThat(tasks.findById(created.id()).orElseThrow().getStatus()).isEqualTo(TaskStatus.COMPLETED);
    for(int index=0;index<3;index++){Quiz quiz=quizzes.save(new Quiz(course.getId(),"Permissions "+index,"Linux permissions"));Question question=questions.save(new Question(quiz.getId(),"Question "+index,"[\"A\",\"B\",\"C\",\"D\"]","A","Explanation","Linux permissions"));answers.save(new AnswerRecord(quiz.getId(),question.getId(),course.getId(),"Linux permissions","B",false));}
    assertThat(analysisSkill.getWeakPoints(course.getId())).anySatisfy(point->{assertThat(point.knowledgePoint()).isEqualTo("Linux permissions");assertThat(point.attemptCount()).isEqualTo(3);assertThat(point.accuracy()).isZero();});}
}
