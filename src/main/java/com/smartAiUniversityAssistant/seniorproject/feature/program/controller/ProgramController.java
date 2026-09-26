package com.smartAiUniversityAssistant.seniorproject.feature.program.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.program.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.program.service.ProgramService;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/programs")
public class ProgramController {
    private final ProgramService programs;
    public ProgramController(ProgramService programs) { this.programs = programs; }

    @PostMapping
    public ResponseEntity<ProgramResponse> create(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody ProgramCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(programs.create(actor, request));
    }
    @GetMapping
    public ResponseEntity<ProgramPageResponse> list(@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(programs.list(actor, ProgramListQuery.from(request)));
    }
    @GetMapping("/summary")
    public ResponseEntity<ProgramSummaryResponse> summary(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(programs.summary(actor));
    }
    @GetMapping("/{id}")
    public ResponseEntity<ProgramResponse> detail(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(programs.detail(actor, id));
    }
    @PutMapping("/{id}")
    public ResponseEntity<ProgramResponse> update(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long id, @Valid @RequestBody ProgramUpdateRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(programs.update(actor, id, request));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id) {
        programs.delete(actor, id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
