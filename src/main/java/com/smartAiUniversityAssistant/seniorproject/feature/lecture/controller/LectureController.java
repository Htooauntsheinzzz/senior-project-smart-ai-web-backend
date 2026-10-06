package com.smartAiUniversityAssistant.seniorproject.feature.lecture.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.lecture.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.lecture.service.LectureService;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/lectures")
public class LectureController {
    private final LectureService lectures;
    public LectureController(LectureService lectures) { this.lectures = lectures; }

    @PostMapping
    public ResponseEntity<LectureResponse> create(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody LectureCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(lectures.create(actor, request));
    }
    @GetMapping
    public ResponseEntity<LecturePageResponse> list(@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(lectures.list(actor, LectureListQuery.from(request)));
    }
    @GetMapping("/summary")
    public ResponseEntity<LectureSummaryResponse> summary(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(lectures.summary(actor));
    }
    @GetMapping("/{id}")
    public ResponseEntity<LectureResponse> detail(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(lectures.detail(actor, id));
    }
    @PutMapping("/{id}")
    public ResponseEntity<LectureResponse> update(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long id, @Valid @RequestBody LectureUpdateRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(lectures.update(actor, id, request));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id) {
        lectures.delete(actor, id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
