package com.smartAiUniversityAssistant.seniorproject.feature.assistant.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AssistantFeedbackService;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Spec file 59: the Admin AI Feedback screen: list, totals, one record, review and export. */
@RestController
@RequestMapping("/api/v1/admin/assistant/feedback")
public class AdminAssistantFeedbackController {
    private final AssistantFeedbackService feedback;
    public AdminAssistantFeedbackController(AssistantFeedbackService feedback) { this.feedback = feedback; }

    @GetMapping
    public ResponseEntity<AdminFeedbackPageResponse> list(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search, @RequestParam(required = false) String rating,
            @RequestParam(required = false) String reviewStatus, @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(feedback.list(actor, page, size, search, rating, reviewStatus, from, to));
    }

    @GetMapping("/statistics")
    public ResponseEntity<AdminFeedbackStatisticsResponse> statistics(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(feedback.statistics(actor));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) String search, @RequestParam(required = false) String rating,
            @RequestParam(required = false) String reviewStatus, @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return AdminAssistantConversationController.csv("ai-feedback.csv",
                feedback.exportCsv(actor, search, rating, reviewStatus, from, to));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminFeedbackResponse> get(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(feedback.detail(actor, id));
    }

    @PatchMapping(value = "/{id}/review", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AdminFeedbackResponse> review(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id,
            @Valid @RequestBody AdminFeedbackReviewRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(feedback.review(actor, id, request));
    }
}
