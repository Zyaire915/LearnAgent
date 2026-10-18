package com.learnagent.workspace;

import java.util.LinkedHashMap;
import java.util.Map;

final class WorkspaceTemplates {
  private WorkspaceTemplates() { }

  static String genericProjectPlan(String name, String description) {
    return """
        # %s

        ## 项目愿景

        %s

        ## 目标与成果

        - 明确项目要解决的问题与目标用户。
        - 收集并整理可追溯的学习资料。
        - 完成一个可展示的核心成果。
        - 记录验证过程、结论与后续改进方向。

        ## 工作阶段

        1. 澄清需求、范围与验收标准。
        2. 调研相关知识和可参考的实现方案。
        3. 设计项目结构并拆分可执行任务。
        4. 实现或制作最小可展示成果。
        5. 验证结果、修复问题并整理展示材料。

        ## 验收标准

        - 项目成果回应原始目标。
        - 关键结论或实现有资料来源支撑。
        - 工作台内包含清晰的计划、任务与成果说明。
        - 能说明已完成内容、验证方式和下一步计划。

        ## 后续 TODO

        - [ ] 确认项目边界与演示重点
        - [ ] 完成资料调研
        - [ ] 产出核心成果
        - [ ] 完成验证与复盘
        """.formatted(name, description);
  }

  static String genericReadme(String name, String description) {
    return "# " + name + "\n\n" + description + "\n\n"
        + "## 如何开始\n\n1. 打开 `PROJECT_PLAN.md`。\n2. 查看学习任务中的项目任务。\n"
        + "3. 阅读 `resources/research.md` 中的资料。\n4. 在工作台继续补充项目成果。\n";
  }

  static String projectPlan(String name) {
    return """
        # %s

        ## 项目目标

        使用 Java 21 与 Spring Boot 3.4.5 开发课程管理 REST 应用。本次 Agent 自动完成 Course CRUD 纵向切片，其余模块作为后续学习任务。

        ## 技术栈

        - Java 21、Maven
        - Spring Boot Web / Validation / Data JPA
        - H2 演示数据库
        - JUnit 5、MockMvc

        ## 架构

        `Controller → Service → Repository → Database`

        ## 数据模型

        Course：`id`、`name`、`description`、`createdAt`。

        ## REST 接口

        - `GET /api/courses`
        - `GET /api/courses/{id}`
        - `POST /api/courses`
        - `PUT /api/courses/{id}`
        - `DELETE /api/courses/{id}`

        ## 里程碑

        1. 理解需求与 REST 资源建模。
        2. 学习 Spring Data JPA 与参数校验。
        3. 完成 Course CRUD。
        4. 完成 Controller 测试与 Maven 构建。
        5. 扩展 Student、Enrollment 与成绩统计模块。

        ## 验收标准

        - `mvn test` 成功。
        - Course CRUD 接口具有校验和明确的 404 行为。
        - README 能指导本地运行。

        ## 后续 TODO

        - [ ] Student CRUD
        - [ ] Enrollment 选课关系
        - [ ] 成绩统计与排名
        - [ ] PostgreSQL 与 Flyway
        - [ ] Vue 管理界面
        """.formatted(name);
  }

  static Map<String, String> springProject(String name) {
    Map<String, String> files = new LinkedHashMap<>();
    files.put("pom.xml", pom());
    files.put("README.md", "# " + name + "\n\n运行：`D:\\LearnAgent\\mvnw.cmd -f pom.xml test`\n\n当前已完成 Course CRUD 纵向切片。\n");
    files.put("src/main/resources/application.yml", """
        spring:
          datasource:
            url: jdbc:h2:mem:courses;DB_CLOSE_DELAY=-1
          jpa:
            hibernate:
              ddl-auto: create-drop
        """);
    files.put("src/main/java/com/example/coursework/CourseworkApplication.java", application());
    files.put("src/main/java/com/example/coursework/course/Course.java", entity());
    files.put("src/main/java/com/example/coursework/course/CourseRepository.java", repository());
    files.put("src/main/java/com/example/coursework/course/CourseRequest.java", request());
    files.put("src/main/java/com/example/coursework/course/CourseResponse.java", response());
    files.put("src/main/java/com/example/coursework/course/CourseService.java", service());
    files.put("src/main/java/com/example/coursework/course/CourseController.java", controller());
    files.put("src/test/java/com/example/coursework/course/CourseControllerTest.java", test());
    return files;
  }

  private static String pom() { return """
      <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
        <modelVersion>4.0.0</modelVersion>
        <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>3.4.5</version></parent>
        <groupId>com.example</groupId><artifactId>course-management</artifactId><version>1.0.0</version>
        <properties><java.version>21</java.version></properties>
        <dependencies>
          <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
          <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
          <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
          <dependency><groupId>com.h2database</groupId><artifactId>h2</artifactId><scope>runtime</scope></dependency>
          <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
        </dependencies>
        <build><plugins><plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugin></plugins></build>
      </project>
      """; }

  private static String application() { return """
      package com.example.coursework;
      import org.springframework.boot.SpringApplication;
      import org.springframework.boot.autoconfigure.SpringBootApplication;
      @SpringBootApplication
      public class CourseworkApplication {
        public static void main(String[] args) { SpringApplication.run(CourseworkApplication.class, args); }
      }
      """; }

  private static String entity() { return """
      package com.example.coursework.course;
      import jakarta.persistence.*;
      import java.time.Instant;
      import java.util.UUID;
      @Entity
      public class Course {
        @Id private UUID id;
        @Column(nullable=false) private String name;
        private String description;
        private Instant createdAt;
        protected Course() {}
        public Course(String name,String description){id=UUID.randomUUID();this.name=name;this.description=description;createdAt=Instant.now();}
        public void update(String name,String description){this.name=name;this.description=description;}
        public UUID getId(){return id;} public String getName(){return name;}
        public String getDescription(){return description;} public Instant getCreatedAt(){return createdAt;}
      }
      """; }

  private static String repository() { return """
      package com.example.coursework.course;
      import java.util.UUID;
      import org.springframework.data.jpa.repository.JpaRepository;
      public interface CourseRepository extends JpaRepository<Course,UUID> {}
      """; }

  private static String request() { return """
      package com.example.coursework.course;
      import jakarta.validation.constraints.NotBlank;
      public record CourseRequest(@NotBlank String name,String description) {}
      """; }

  private static String response() { return """
      package com.example.coursework.course;
      import java.time.Instant;
      import java.util.UUID;
      public record CourseResponse(UUID id,String name,String description,Instant createdAt) {
        static CourseResponse from(Course value){return new CourseResponse(value.getId(),value.getName(),value.getDescription(),value.getCreatedAt());}
      }
      """; }

  private static String service() { return """
      package com.example.coursework.course;
      import java.util.*;
      import org.springframework.http.HttpStatus;
      import org.springframework.stereotype.Service;
      import org.springframework.transaction.annotation.Transactional;
      import org.springframework.web.server.ResponseStatusException;
      @Service
      public class CourseService {
        private final CourseRepository repository;
        public CourseService(CourseRepository repository){this.repository=repository;}
        @Transactional(readOnly=true) public List<CourseResponse> list(){return repository.findAll().stream().map(CourseResponse::from).toList();}
        @Transactional(readOnly=true) public CourseResponse get(UUID id){return CourseResponse.from(entity(id));}
        @Transactional public CourseResponse create(CourseRequest request){return CourseResponse.from(repository.save(new Course(request.name().trim(),request.description())));}
        @Transactional public CourseResponse update(UUID id,CourseRequest request){Course value=entity(id);value.update(request.name().trim(),request.description());return CourseResponse.from(value);}
        @Transactional public void delete(UUID id){repository.delete(entity(id));}
        private Course entity(UUID id){return repository.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Course not found"));}
      }
      """; }

  private static String controller() { return """
      package com.example.coursework.course;
      import jakarta.validation.Valid;
      import java.util.*;
      import org.springframework.http.*;
      import org.springframework.web.bind.annotation.*;
      @RestController @RequestMapping("/api/courses")
      public class CourseController {
        private final CourseService service;
        public CourseController(CourseService service){this.service=service;}
        @GetMapping public List<CourseResponse> list(){return service.list();}
        @GetMapping("/{id}") public CourseResponse get(@PathVariable UUID id){return service.get(id);}
        @PostMapping public ResponseEntity<CourseResponse> create(@Valid @RequestBody CourseRequest request){return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));}
        @PutMapping("/{id}") public CourseResponse update(@PathVariable UUID id,@Valid @RequestBody CourseRequest request){return service.update(id,request);}
        @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id){service.delete(id);}
      }
      """; }

  private static String test() { return """
      package com.example.coursework.course;
      import static org.mockito.Mockito.*;
      import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
      import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
      import java.time.Instant;
      import java.util.*;
      import org.junit.jupiter.api.Test;
      import org.springframework.beans.factory.annotation.Autowired;
      import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
      import org.springframework.test.context.bean.override.mockito.MockitoBean;
      import org.springframework.test.web.servlet.MockMvc;
      @WebMvcTest(CourseController.class)
      class CourseControllerTest {
        @Autowired MockMvc mvc;
        @MockitoBean CourseService service;
        @Test void listsCourses() throws Exception {
          when(service.list()).thenReturn(List.of(new CourseResponse(UUID.randomUUID(),"Java",null,Instant.now())));
          mvc.perform(get("/api/courses")).andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Java"));
        }
        @Test void rejectsBlankName() throws Exception {
          mvc.perform(post("/api/courses").contentType("application/json").content("{\\\"name\\\":\\\"\\\"}"))
              .andExpect(status().isBadRequest());
        }
      }
      """; }
}
