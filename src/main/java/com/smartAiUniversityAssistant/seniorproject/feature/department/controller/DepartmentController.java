package com.smartAiUniversityAssistant.seniorproject.feature.department.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.department.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.department.service.DepartmentService;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/departments")
public class DepartmentController {
    private final DepartmentService departments;
    public DepartmentController(DepartmentService departments) { this.departments = departments; }

    @PostMapping
    public ResponseEntity<DepartmentResponse> create(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody DepartmentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(departments.create(actor, request));
    }
    @GetMapping
    public ResponseEntity<DepartmentPageResponse> list(@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(departments.list(actor, DepartmentListQuery.from(request)));
    }
    @GetMapping("/summary")
    public ResponseEntity<DepartmentSummaryResponse> summary(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(departments.summary(actor));
    }
    @GetMapping("/{id}")
    public ResponseEntity<DepartmentResponse> detail(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(departments.detail(actor, id));
    }
    @PutMapping("/{id}")
    public ResponseEntity<DepartmentResponse> update(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long id, @Valid @RequestBody DepartmentUpdateRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(departments.update(actor, id, request));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id) {
        departments.delete(actor, id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
