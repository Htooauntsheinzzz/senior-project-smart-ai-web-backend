package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.DocumentCategoryService;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/knowledge/categories")
public class AdminDocumentCategoryController {
    private final DocumentCategoryService categories;
    public AdminDocumentCategoryController(DocumentCategoryService categories) { this.categories = categories; }

    @GetMapping
    public ResponseEntity<DocumentCategoryPageResponse> list(@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(categories.list(actor, DocumentCategoryListQuery.from(request)));
    }
    @GetMapping("/{id}")
    public ResponseEntity<DocumentCategoryResponse> detail(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(categories.detail(actor, id));
    }
    @PostMapping
    public ResponseEntity<DocumentCategoryResponse> create(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody DocumentCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(categories.create(actor, request));
    }
    @PutMapping("/{id}")
    public ResponseEntity<DocumentCategoryResponse> update(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long id, @Valid @RequestBody DocumentCategoryRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(categories.update(actor, id, request));
    }
    @PatchMapping("/{id}/activate")
    public ResponseEntity<DocumentCategoryResponse> activate(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(categories.activate(actor, id));
    }
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<DocumentCategoryResponse> deactivate(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(categories.deactivate(actor, id));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id) {
        categories.delete(actor, id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
