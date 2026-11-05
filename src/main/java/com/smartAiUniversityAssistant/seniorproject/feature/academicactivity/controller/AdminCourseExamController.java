package com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.service.CourseExamService;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/exams")
public class AdminCourseExamController {
    private final CourseExamService exams;
    public AdminCourseExamController(CourseExamService exams) { this.exams = exams; }

    @GetMapping
    public ResponseEntity<AcademicActivityPageResponse<CourseExamResponse>> list(@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(exams.list(actor, AcademicActivityListQuery.from(request)));
    }
    @GetMapping("/{id:\\d+}")
    public ResponseEntity<CourseExamResponse> detail(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(exams.detail(actor, id));
    }
    @PostMapping
    public ResponseEntity<CourseExamResponse> create(@AuthenticationPrincipal AuthenticatedUser actor, @Valid @RequestBody CourseExamRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(exams.create(actor, request));
    }
    @PutMapping("/{id:\\d+}")
    public ResponseEntity<CourseExamResponse> update(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id,
            @Valid @RequestBody CourseExamRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(exams.update(actor, id, request));
    }
    @PostMapping("/{id:\\d+}/schedule")
    public ResponseEntity<CourseExamResponse> schedule(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(exams.schedule(actor, id));
    }
    @PostMapping("/{id:\\d+}/cancel")
    public ResponseEntity<CourseExamResponse> cancel(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(exams.cancel(actor, id));
    }
    @PostMapping("/{id:\\d+}/complete")
    public ResponseEntity<CourseExamResponse> complete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(exams.complete(actor, id));
    }
    @DeleteMapping("/{id:\\d+}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        exams.delete(actor, id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
