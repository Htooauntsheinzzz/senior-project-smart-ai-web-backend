package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto.KnowledgeIndexStatisticsResponse;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.KnowledgeIndexService;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/knowledge/index")
public class AdminKnowledgeIndexController {
    private final KnowledgeIndexService index;

    public AdminKnowledgeIndexController(KnowledgeIndexService index) { this.index = index; }

    @GetMapping("/statistics")
    public ResponseEntity<KnowledgeIndexStatisticsResponse> statistics(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(index.statistics(actor));
    }
}
