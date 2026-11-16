package com.smartAiUniversityAssistant.seniorproject.feature.assistant.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.AdminConversationDetailResponse;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.AdminConversationPageResponse;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AdminAssistantHistoryService;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Spec file 59: the Admin Chat History screen. Read-only; Admins cannot change or delete a Student's conversation. */
@RestController
@RequestMapping("/api/v1/admin/assistant/conversations")
public class AdminAssistantConversationController {
    private final AdminAssistantHistoryService history;
    public AdminAssistantConversationController(AdminAssistantHistoryService history) { this.history = history; }

    @GetMapping
    public ResponseEntity<AdminConversationPageResponse> list(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search, @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(history.list(actor, page, size, search, from, to));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) String search, @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return csv("ai-chat-history.csv", history.exportCsv(actor, search, from, to));
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<AdminConversationDetailResponse> get(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable String conversationId, @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) Long beforeMessageId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(history.detail(actor, conversationId, limit, beforeMessageId));
    }

    static ResponseEntity<byte[]> csv(String filename, byte[] body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(body);
    }
}
