package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.controller;

import com.smartAiUniversityAssistant.seniorproject.exception.ApiErrorWriter;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.exception.KnowledgeFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.KnowledgeDocumentService;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/knowledge/documents")
public class AdminKnowledgeDocumentController {
    private static final Set<String> UPLOAD_FIELDS = Set.of("title", "categoryId", "academicYearLabel", "description",
            "curriculumVersionId", "programId", "departmentId");
    private final KnowledgeDocumentService documents;
    private final ApiErrorWriter errors;
    public AdminKnowledgeDocumentController(KnowledgeDocumentService documents, ApiErrorWriter errors) {
        this.documents = documents;
        this.errors = errors;
    }

    @GetMapping
    public ResponseEntity<KnowledgeDocumentPageResponse> list(@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(documents.search(actor, KnowledgeDocumentListQuery.from(request)));
    }
    @GetMapping("/statistics")
    public ResponseEntity<KnowledgeDocumentStatisticsResponse> statistics(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(documents.statistics(actor));
    }
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<KnowledgeDocumentUploadResponse> upload(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @ModelAttribute KnowledgeDocumentUploadRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file, HttpServletRequest servletRequest) {
        if (!UPLOAD_FIELDS.containsAll(servletRequest.getParameterMap().keySet())
                || servletRequest.getParameterMap().values().stream().anyMatch(values -> values.length != 1))
            throw KnowledgeFailure.uploadValidation();
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(documents.upload(actor, request, file));
    }
    @GetMapping("/{id}")
    public ResponseEntity<KnowledgeDocumentResponse> detail(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(documents.getById(actor, id));
    }
    @PutMapping("/{id}")
    public ResponseEntity<KnowledgeDocumentResponse> update(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long id, @Valid @RequestBody KnowledgeDocumentUpdateRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(documents.updateMetadata(actor, id, request));
    }
    @PatchMapping("/{id}/activate")
    public ResponseEntity<KnowledgeDocumentResponse> activate(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(documents.activate(actor, id));
    }
    @PatchMapping("/{id}/archive")
    public ResponseEntity<KnowledgeDocumentResponse> archive(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(documents.archive(actor, id));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id) {
        documents.delete(actor, id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @PostMapping(value = "/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<KnowledgeDocumentUploadResponse> replaceFile(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long id, @RequestPart(value = "file", required = false) MultipartFile file, HttpServletRequest request) {
        if (!request.getParameterMap().isEmpty()) throw KnowledgeFailure.uploadValidation();
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(documents.replaceFile(actor, id, file));
    }
    @GetMapping("/{id}/files")
    public ResponseEntity<List<KnowledgeDocumentFileResponse>> revisions(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(documents.revisions(actor, id));
    }
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id) {
        return attachment(documents.download(actor, id, null));
    }
    @GetMapping("/{id}/files/{fileId}/download")
    public ResponseEntity<Resource> downloadRevision(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long id, @PathVariable Long fileId) {
        if (fileId <= 0) throw KnowledgeFailure.invalid();
        return attachment(documents.download(actor, id, fileId));
    }
    @GetMapping("/{id}/processing")
    public ResponseEntity<KnowledgeDocumentProcessingResponse> processing(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(documents.processing(actor, id));
    }
    /**
     * Queues the current revision for the ingestion worker and answers 202 with the processing body at once. The
     * optional body selects the mode (RESUME by default, or FULL); unknown values or properties are 400.
     */
    @PostMapping("/{id}/reprocess")
    public ResponseEntity<KnowledgeDocumentProcessingResponse> reprocess(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long id, @RequestBody(required = false) tools.jackson.databind.JsonNode body) {
        var mode = KnowledgeDocumentReprocessRequest.from(body).effectiveMode();
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(documents.reprocess(actor, id, mode));
    }

    /** Binding errors on the multipart form use the upload error code; JSON bodies keep VALIDATION_ERROR. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    void invalid(MethodArgumentNotValidException e, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String type = request.getContentType();
        boolean multipart = type != null && type.toLowerCase(Locale.ROOT).startsWith("multipart/");
        errors.write(request, response, 400, multipart ? "DOCUMENT_UPLOAD_VALIDATION_ERROR" : "VALIDATION_ERROR",
                multipart ? KnowledgeFailure.uploadValidation().getMessage() : "The request is invalid.");
    }

    private ResponseEntity<Resource> attachment(KnowledgeDocumentDownload download) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.mimeType()))
                .contentLength(download.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(download.filename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(new InputStreamResource(download.content()));
    }
}
