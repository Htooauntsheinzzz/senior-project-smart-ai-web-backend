package com.smartAiUniversityAssistant.seniorproject.feature.coursesection.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.service.CourseSectionService;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/course-sections")
public class CourseSectionController {
    private final CourseSectionService sections;
    public CourseSectionController(CourseSectionService sections) { this.sections = sections; }

    @PostMapping
    public ResponseEntity<CourseSectionResponse> create(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody CourseSectionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(sections.create(actor, request));
    }
    @GetMapping
    public ResponseEntity<CourseSectionPageResponse> list(@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sections.list(actor, CourseSectionListQuery.from(request)));
    }
    @GetMapping("/summary")
    public ResponseEntity<CourseSectionSummaryResponse> summary(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sections.summary(actor));
    }
    @GetMapping("/{id}")
    public ResponseEntity<CourseSectionResponse> detail(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sections.detail(actor, id));
    }
    @PutMapping("/{id}")
    public ResponseEntity<CourseSectionResponse> update(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long id, @Valid @RequestBody CourseSectionUpdateRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sections.update(actor, id, request));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id) {
        sections.delete(actor, id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
