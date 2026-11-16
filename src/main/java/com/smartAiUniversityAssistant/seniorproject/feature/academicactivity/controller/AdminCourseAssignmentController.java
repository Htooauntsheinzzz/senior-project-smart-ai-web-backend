package com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.service.CourseAssignmentService;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/assignments")
public class AdminCourseAssignmentController {
    private final CourseAssignmentService assignments;
    public AdminCourseAssignmentController(CourseAssignmentService assignments) { this.assignments = assignments; }

    @GetMapping
    public ResponseEntity<AcademicActivityPageResponse<CourseAssignmentResponse>> list(@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(assignments.list(actor, AcademicActivityListQuery.from(request)));
    }
    @GetMapping("/{id:\\d+}")
    public ResponseEntity<CourseAssignmentResponse> detail(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(assignments.detail(actor, id));
    }
    @PostMapping
    public ResponseEntity<CourseAssignmentResponse> create(@AuthenticationPrincipal AuthenticatedUser actor, @Valid @RequestBody CourseAssignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(assignments.create(actor, request));
    }
    @PutMapping("/{id:\\d+}")
    public ResponseEntity<CourseAssignmentResponse> update(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id,
            @Valid @RequestBody CourseAssignmentRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(assignments.update(actor, id, request));
    }
    @PostMapping("/{id:\\d+}/publish")
    public ResponseEntity<CourseAssignmentResponse> publish(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(assignments.publish(actor, id));
    }
    @PostMapping("/{id:\\d+}/close")
    public ResponseEntity<CourseAssignmentResponse> close(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(assignments.close(actor, id));
    }
    @DeleteMapping("/{id:\\d+}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        assignments.delete(actor, id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
