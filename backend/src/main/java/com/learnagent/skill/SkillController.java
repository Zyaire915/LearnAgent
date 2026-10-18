package com.learnagent.skill;
import java.util.List;import org.springframework.web.bind.annotation.*;
@RestController@RequestMapping("/api/skills")public class SkillController{private final SkillStatusService service;public SkillController(SkillStatusService service){this.service=service;}@GetMapping public List<SkillStatusService.SkillStatus> list(){return service.statuses();}}
