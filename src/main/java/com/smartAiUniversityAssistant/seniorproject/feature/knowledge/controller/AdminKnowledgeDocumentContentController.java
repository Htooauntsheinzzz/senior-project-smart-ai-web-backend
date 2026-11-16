package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.KnowledgeDocumentContentService;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Read-only inspection of the pages (spec file 47) and chunks (spec file 49) of the current revision. Never a vector. */
@RestController
@RequestMapping("/api/v1/admin/knowledge/documents/{id}")
public class AdminKnowledgeDocumentContentController {
    private final KnowledgeDocumentContentService content;

    public AdminKnowledgeDocumentContentController(KnowledgeDocumentContentService content) { this.content = content; }

    @GetMapping("/pages")
    public ResponseEntity<DocumentTextPageListResponse> pages(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long id, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(content.pages(actor, id, DocumentTextPageListQuery.from(request)));
    }

    @GetMapping("/chunks")
    public ResponseEntity<DocumentChunkListResponse> chunks(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long id, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(content.chunks(actor, id, DocumentChunkListQuery.from(request)));
    }

    @GetMapping("/chunks/{chunkIndex}")
    public ResponseEntity<DocumentChunkResponse> chunk(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long id, @PathVariable Long chunkIndex) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(content.chunk(actor, id, chunkIndex));
    }

    @GetMapping("/pages/{pageNumber}")
    public ResponseEntity<DocumentTextPageResponse> page(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long id, @PathVariable Long pageNumber) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(content.page(actor, id, pageNumber));
    }
}
