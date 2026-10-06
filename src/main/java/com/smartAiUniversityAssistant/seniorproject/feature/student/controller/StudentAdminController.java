package com.smartAiUniversityAssistant.seniorproject.feature.student.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.student.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.student.service.StudentService;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/students")
public class StudentAdminController {
    private final StudentService students;
    public StudentAdminController(StudentService students) { this.students = students; }

    @PostMapping
    public ResponseEntity<StudentResponse> create(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody StudentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(students.create(actor, request));
    }
    @GetMapping
    public ResponseEntity<StudentPageResponse> list(@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(students.list(actor, StudentListQuery.from(request)));
    }
    @GetMapping("/summary")
    public ResponseEntity<StudentSummaryResponse> summary(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(students.summary(actor));
    }
    @PostMapping("/{id:\\d+}/reset-password")
    public ResponseEntity<Void> resetPassword(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id,
            @Valid @RequestBody StudentPasswordResetRequest request) {
        students.resetPassword(actor, id, request);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @GetMapping("/{id:\\d+}")
    public ResponseEntity<StudentResponse> detail(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(students.detail(actor, id));
    }
    @PutMapping("/{id:\\d+}")
    public ResponseEntity<StudentResponse> update(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id,
            @Valid @RequestBody StudentUpdateRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(students.update(actor, id, request));
    }
    @DeleteMapping("/{id:\\d+}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        students.delete(actor, id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
