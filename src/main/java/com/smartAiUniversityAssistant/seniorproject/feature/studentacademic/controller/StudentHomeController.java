package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto.StudentHomeSummaryResponse;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service.StudentHomeService;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedStudent;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/student/home")
public class StudentHomeController {
    private final StudentHomeService home;
    public StudentHomeController(StudentHomeService home) { this.home = home; }

    @GetMapping("/summary")
    public ResponseEntity<StudentHomeSummaryResponse> summary(@AuthenticationPrincipal AuthenticatedStudent student) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(home.getSummary(student.studentId()));
    }
}
