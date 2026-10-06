package com.smartAiUniversityAssistant.seniorproject.feature.semester.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.semester.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.semester.service.SemesterService;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/semesters")
public class SemesterController {
    private final SemesterService semesters;
    public SemesterController(SemesterService semesters) { this.semesters = semesters; }

    @PostMapping
    public ResponseEntity<SemesterResponse> create(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody SemesterCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(semesters.create(actor, request));
    }
    @GetMapping
    public ResponseEntity<SemesterPageResponse> list(@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(semesters.list(actor, SemesterListQuery.from(request)));
    }
    @GetMapping("/{id}")
    public ResponseEntity<SemesterResponse> detail(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(semesters.detail(actor, id));
    }
    @PutMapping("/{id}")
    public ResponseEntity<SemesterResponse> update(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long id, @Valid @RequestBody SemesterUpdateRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(semesters.update(actor, id, request));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id) {
        semesters.delete(actor, id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
