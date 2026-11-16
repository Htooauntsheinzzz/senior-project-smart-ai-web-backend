package com.smartAiUniversityAssistant.seniorproject.feature.assistant.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.AdminAssistantAnswerResponse;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.AdminAssistantChatRequest;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.AdminKnowledgeTestRequest;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AdminAssistantService;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Spec file 59: the assistant for Admins (AI Assistant and Test AI Knowledge screens). Route security (admin token,
 * SUPER_ADMIN, ADMIN or ACADEMIC_ADMIN, no forced password change) comes from the /api/v1/admin/assistant/** rule.
 */
@RestController
@RequestMapping("/api/v1/admin/assistant")
public class AdminAssistantController {
    private final AdminAssistantService assistant;
    public AdminAssistantController(AdminAssistantService assistant) { this.assistant = assistant; }

    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AdminAssistantAnswerResponse> chat(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody AdminAssistantChatRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(assistant.chat(actor, request));
    }

    @PostMapping(value = "/knowledge-test", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AdminAssistantAnswerResponse> testKnowledge(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody AdminKnowledgeTestRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(assistant.testKnowledge(actor, request));
    }
}
