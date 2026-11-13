package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.KnowledgeSearchService;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Admin semantic search (spec file 52, section 13). A POST although it changes nothing: the question can be long and must
 * not appear in URLs, proxy logs or browser history. Students have no search endpoint.
 */
@RestController
@RequestMapping("/api/v1/admin/knowledge/search")
public class AdminKnowledgeSearchController {
    private final KnowledgeSearchService search;

    public AdminKnowledgeSearchController(KnowledgeSearchService search) { this.search = search; }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<KnowledgeSearchResponse> search(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody KnowledgeSearchRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(search.adminSearch(actor, request));
    }
}
