package com.smartAiUniversityAssistant.seniorproject.feature.assistant.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.AssistantConversationDetailResponse;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.AssistantConversationPageResponse;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AssistantConversationHistoryService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/**
 * Step 17 (spec file 58): the Student's own stored conversations. Continuing one is POST /chat with its conversationId.
 * Route security comes from the /api/v1/student/** rule; the Student is the token's Student, never a parameter.
 */
@RestController
@RequestMapping("/api/v1/student/assistant/conversations")
public class StudentAssistantConversationController {
    private final AssistantConversationHistoryService history;
    public StudentAssistantConversationController(AssistantConversationHistoryService history) { this.history = history; }

    @GetMapping
    public ResponseEntity<AssistantConversationPageResponse> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(history.listMyConversations(page, size));
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<AssistantConversationDetailResponse> get(@PathVariable String conversationId,
            @RequestParam(defaultValue = "50") int limit, @RequestParam(required = false) Long beforeMessageId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(history.getMyConversation(conversationId, limit, beforeMessageId));
    }

    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> delete(@PathVariable String conversationId) {
        history.deleteMyConversation(conversationId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
