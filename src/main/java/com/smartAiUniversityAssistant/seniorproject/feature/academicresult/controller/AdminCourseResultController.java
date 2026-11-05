package com.smartAiUniversityAssistant.seniorproject.feature.academicresult.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.academicresult.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.academicresult.service.CourseResultService;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/academic/course-results")
public class AdminCourseResultController {
    private final CourseResultService results;
    public AdminCourseResultController(CourseResultService results) { this.results = results; }

    @GetMapping
    public ResponseEntity<CourseResultPageResponse> list(@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(results.list(actor, CourseResultListQuery.from(request)));
    }
    @PostMapping
    public ResponseEntity<CourseResultResponse> create(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody CourseResultCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(results.create(actor, request));
    }
    @PutMapping("/{id:\\d+}")
    public ResponseEntity<CourseResultResponse> update(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id,
            @Valid @RequestBody CourseResultUpdateRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(results.update(actor, id, request));
    }
    @PostMapping("/{id:\\d+}/finalize")
    public ResponseEntity<CourseResultResponse> finalizeResult(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(results.finalizeResult(actor, id));
    }
}
