package com.smartAiUniversityAssistant.seniorproject.feature.enrollment.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.service.EnrollmentService;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/enrollments")
public class AdminEnrollmentController {
    private final EnrollmentService enrollments;
    public AdminEnrollmentController(EnrollmentService enrollments) { this.enrollments = enrollments; }

    @PostMapping
    public ResponseEntity<EnrollmentResponse> create(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody EnrollmentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(enrollments.create(actor, request));
    }
    @GetMapping
    public ResponseEntity<EnrollmentPageResponse> list(@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(enrollments.list(actor, EnrollmentListQuery.from(request)));
    }
    @GetMapping("/summary")
    public ResponseEntity<EnrollmentSummaryResponse> summary(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(enrollments.summary(actor));
    }
    @PatchMapping("/{id:\\d+}/status")
    public ResponseEntity<EnrollmentResponse> updateStatus(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable long id, @Valid @RequestBody EnrollmentStatusUpdateRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(enrollments.updateStatus(actor, id, request));
    }
    @GetMapping("/{id:\\d+}")
    public ResponseEntity<EnrollmentResponse> detail(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(enrollments.detail(actor, id));
    }
    @PutMapping("/{id:\\d+}")
    public ResponseEntity<EnrollmentResponse> update(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id,
            @Valid @RequestBody EnrollmentUpdateRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(enrollments.update(actor, id, request));
    }
    @DeleteMapping("/{id:\\d+}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        enrollments.delete(actor, id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
