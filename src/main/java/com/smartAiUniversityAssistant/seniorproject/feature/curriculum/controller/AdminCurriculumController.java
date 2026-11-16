package com.smartAiUniversityAssistant.seniorproject.feature.curriculum.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.curriculum.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.curriculum.service.CurriculumVersionService;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/curriculums")
public class AdminCurriculumController {
    private final CurriculumVersionService curriculums;
    public AdminCurriculumController(CurriculumVersionService curriculums) { this.curriculums = curriculums; }

    @GetMapping
    public ResponseEntity<CurriculumPageResponse> list(@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(curriculums.list(actor, CurriculumListQuery.from(request)));
    }
    @GetMapping("/{id}")
    public ResponseEntity<CurriculumResponse> detail(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(curriculums.detail(actor, id));
    }
    @PostMapping
    public ResponseEntity<CurriculumResponse> create(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody CurriculumCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(curriculums.create(actor, request));
    }
    @PutMapping("/{id}")
    public ResponseEntity<CurriculumResponse> update(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long id, @Valid @RequestBody CurriculumUpdateRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(curriculums.update(actor, id, request));
    }
    @PatchMapping("/{id}/activate")
    public ResponseEntity<CurriculumResponse> activate(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(curriculums.activate(actor, id));
    }
    @PatchMapping("/{id}/archive")
    public ResponseEntity<CurriculumResponse> archive(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(curriculums.archive(actor, id));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id) {
        curriculums.delete(actor, id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
