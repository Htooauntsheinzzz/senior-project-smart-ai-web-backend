package com.smartAiUniversityAssistant.seniorproject.feature.assistant.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.AssistantFeedbackRequest;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.AssistantFeedbackResponse;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AssistantFeedbackService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/**
 * Spec file 59: the Student rates one answer of their own conversation (thumbs up or down, optional comment). Rating
 * the same answer again replaces the rating. Route security comes from the /api/v1/student/** rule; the Student is the
 * token's Student, never a parameter.
 */
@RestController
@RequestMapping("/api/v1/student/assistant/conversations/{conversationId}/feedback")
public class StudentAssistantFeedbackController {
    private final AssistantFeedbackService feedback;
    public StudentAssistantFeedbackController(AssistantFeedbackService feedback) { this.feedback = feedback; }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AssistantFeedbackResponse> submit(@PathVariable String conversationId,
            @Valid @RequestBody AssistantFeedbackRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(feedback.submitMyFeedback(conversationId, request));
    }
}
