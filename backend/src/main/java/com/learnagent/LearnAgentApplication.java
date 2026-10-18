package com.learnagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LearnAgentApplication {
  public static void main(String[] args) { SpringApplication.run(LearnAgentApplication.class, args); }
}
