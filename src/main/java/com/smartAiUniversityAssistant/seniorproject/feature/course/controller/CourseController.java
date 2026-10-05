package com.smartAiUniversityAssistant.seniorproject.feature.course.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.course.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.course.service.CourseService;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/courses")
public class CourseController {
    private final CourseService courses;
    public CourseController(CourseService courses) { this.courses = courses; }

    @PostMapping
    public ResponseEntity<CourseResponse> create(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody CourseCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(courses.create(actor, request));
    }
    @GetMapping
    public ResponseEntity<CoursePageResponse> list(@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(courses.list(actor, CourseListQuery.from(request)));
    }
    @GetMapping("/summary")
    public ResponseEntity<CourseSummaryResponse> summary(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(courses.summary(actor));
    }
    @GetMapping("/{id}")
    public ResponseEntity<CourseResponse> detail(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(courses.detail(actor, id));
    }
    @PutMapping("/{id}")
    public ResponseEntity<CourseResponse> update(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long id, @Valid @RequestBody CourseUpdateRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(courses.update(actor, id, request));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id) {
        courses.delete(actor, id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
