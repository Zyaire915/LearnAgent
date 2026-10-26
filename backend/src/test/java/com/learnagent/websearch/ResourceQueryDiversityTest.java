package com.learnagent.websearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import com.learnagent.digest.HiddenResourceSourceRepository;
import com.learnagent.websearch.WebSearchProvider.WebSearchResult;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ResourceQueryDiversityTest {
  private WebSearchResult item(String title, String snippet, String url) {
    return new WebSearchResult(title, snippet, url, "source", null);
  }

  @Test void multiTopicAcceptsIndividualLessonsWithoutRequiringAllSixSubjectsInEachTitle() {
    String query = "线性表、树、图、查找、排序及算法分析";
    assertThat(ResourceQuery.topics(query)).containsExactly("线性表", "树", "图", "查找", "排序", "算法分析");
    var candidates = List.of(item("线性表的顺序存储", "", "https://a.example/list"),
        item("二叉树与遍历", "", "https://b.example/tree"), item("图论与图遍历", "", "https://c.example/graph"),
        item("查找算法", "", "https://d.example/search"), item("排序算法", "", "https://e.example/sort"),
        item("算法分析与复杂度", "", "https://f.example/analysis"),
        item("图解 CPU", "", "https://g.example/unrelated"));
    var selected = ResourceSelection.select(query, candidates, List.of(), 5, Instant.now());
    assertThat(selected).hasSize(5).noneMatch(r -> r.url().contains("unrelated"));
    for (var candidate : candidates.subList(0, 6))
      assertThat(ResourceRelevance.evaluate(query, candidate.title(), candidate.snippet()).sufficient()).isTrue();
  }

  @Test void aluAliasesMatchButGenericComputerAndCpuPagesDoNotPass() {
    var selected = ResourceSelection.select("计算机ALU工作原理", List.of(
        item("算术逻辑单元", "", "https://a.example/alu"),
        item("Arithmetic Logic Unit", "", "https://b.example/alu"),
        item("ALU 的设计", "", "https://c.example/alu"),
        item("计算机工作原理详解", "", "https://mit.edu/generic"),
        item("CPU工作原理", "", "https://mit.edu/cpu")), List.of(), 5, Instant.now());
    assertThat(selected).hasSize(3).allMatch(r -> r.url().endsWith("alu"));
    assertThat(ResourceRelevance.evaluate("CPU工作原理详解", "中央处理器", "").sufficient()).isTrue();
  }

  @Test void spreadsSourcesAndLimitsSingleSiteWithoutFillingWithCopies() {
    var candidates = List.of(item("CPU工作原理详解", "", "https://docs.oracle.com/cpu/a"),
        item("CPU工作原理详解", "不同的截取位置", "https://docs.oracle.com/cpu/b"),
        item("CPU 指令流水线", "", "https://blogs.oracle.com/pipeline"),
        item("CPU 中断处理", "", "https://docs.oracle.com/interrupt"),
        item("CPU 基础课程", "", "https://a.example/cpu"),
        item("CPU 取指与译码", "", "https://b.example/cpu"),
        item("CPU 缓存", "", "https://c.example/cpu"));
    var selected = ResourceSelection.select("CPU工作原理详解", candidates, List.of(), 5, Instant.now());
    assertThat(selected).hasSize(5);
    assertThat(selected.stream().filter(r -> ResourceSelection.sourceGroup(r.url()).equals("oracle.com"))).hasSize(2);
    assertThat(selected).noneMatch(r -> r.url().endsWith("/b"));
    assertThat(selected.subList(0, 4).stream().map(r -> ResourceSelection.sourceGroup(r.url())).distinct()).hasSize(4);
    assertThat(ResourceSelection.select("CPU工作原理详解", candidates.subList(0, 4), List.of(), 5, Instant.now())).hasSize(2);
  }

  @Test void groupsMultiTopicSearchesWithinFourRequests() {
    var builder = RestClient.builder();
    var server = MockRestServiceServer.bindTo(builder).build();
    String query = "线性表、树、图、查找、排序及算法分析";
    for (String expected : List.of(query, "线性表 树 课程 教程", "图 查找 课程 教程", "排序 算法分析 课程 教程")) {
      server.expect(requestTo("https://search.example/search")).andExpect(jsonPath("$.query").value(expected))
          .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));
    }
    assertThat(new TavilyWebSearchProvider("test-key", "https://search.example/search", builder,
        mock(HiddenResourceSourceRepository.class), mock(ArticleContentReviewer.class)).search(query, 5)).isEmpty();
    server.verify();
  }
}
