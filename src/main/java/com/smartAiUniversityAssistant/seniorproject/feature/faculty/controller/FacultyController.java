package com.smartAiUniversityAssistant.seniorproject.feature.faculty.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.faculty.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.faculty.service.FacultyService;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/faculties")
public class FacultyController {
    private final FacultyService faculties;

    public FacultyController(FacultyService faculties) { this.faculties = faculties; }

    @PostMapping
    public ResponseEntity<FacultyResponse> create(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody FacultyCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(faculties.create(actor, request));
    }

    @GetMapping
    public ResponseEntity<FacultyPageResponse> list(@AuthenticationPrincipal AuthenticatedUser actor,
            HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(faculties.list(actor, FacultyListQuery.from(request)));
    }

    @GetMapping("/summary")
    public ResponseEntity<FacultySummaryResponse> summary(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(faculties.summary(actor));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FacultyResponse> detail(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(faculties.detail(actor, id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FacultyResponse> update(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long id, @Valid @RequestBody FacultyUpdateRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(faculties.update(actor, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id) {
        faculties.delete(actor, id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
