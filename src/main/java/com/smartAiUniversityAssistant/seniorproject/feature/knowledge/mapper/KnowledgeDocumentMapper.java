package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.mapper;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.entity.*;
import com.smartAiUniversityAssistant.seniorproject.feature.curriculum.entity.CurriculumVersion;
import com.smartAiUniversityAssistant.seniorproject.feature.department.entity.Department;
import com.smartAiUniversityAssistant.seniorproject.feature.program.entity.Program;
import java.time.*;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeDocumentMapper {
    /** The current revision of a document with its processing record and uploader name (any of them may be null). */
    public record CurrentFile(AcademicDocumentFile file, AcademicDocumentProcessing processing, String uploaderName) {}

    public KnowledgeDocumentResponse response(AcademicDocument d, CurrentFile current) {
        DocumentCategory category = d.getCategory();
        AcademicDocumentFile file = current == null ? null : current.file();
        AcademicDocumentProcessing processing = current == null ? null : current.processing();
        return new KnowledgeDocumentResponse(d.getId(), d.getTitle(), d.getDescription(),
                new KnowledgeDocumentResponse.Category(category.getId(), category.getCategoryCode(),
                        category.getCategoryName(), category.isActive() && !category.isDeleted()),
                d.getAcademicYearLabel(), scope(d),
                file == null ? null : new KnowledgeDocumentResponse.FileSummary(file.getId(), file.getOriginalFilename(),
                        file.getFileExtension(), file.getMimeType(), file.getSizeBytes(), file.getRevisionNumber()),
                file == null ? null : new KnowledgeDocumentResponse.Uploader(file.getUploadedBy(), current.uploaderName()),
                file == null ? null : instant(file.getUploadedAt()),
                processing == null ? null : processing.getStatus().name(), d.isActive(),
                d.getCreatedBy(), instant(d.getCreatedAt()), d.getUpdatedBy(), instant(d.getUpdatedAt()));
    }

    public KnowledgeDocumentPageResponse page(Page<AcademicDocument> page, KnowledgeDocumentListQuery query,
            Map<Long, CurrentFile> current) {
        return new KnowledgeDocumentPageResponse(page.getContent().stream()
                .map(d -> response(d, current.get(d.getId()))).toList(),
                query.page(), query.size(), page.getTotalElements(), page.getTotalPages(),
                query.page() == 0, query.page() >= page.getTotalPages() - 1, query.sort());
    }

    private KnowledgeDocumentResponse.Scope scope(AcademicDocument d) {
        CurriculumVersion curriculum = d.getCurriculumVersion();
        if (curriculum != null) {
            Program program = curriculum.getProgram();
            return new KnowledgeDocumentResponse.Scope("CURRICULUM", curriculum.getId(), curriculum.getVersionCode(),
                    curriculum.getStatus().name(), program.getId(), program.getProgramName(),
                    program.getDepartment().getId(), program.getDepartment().getDepartmentName());
        }
        if (d.getProgram() != null) {
            Program program = d.getProgram();
            return new KnowledgeDocumentResponse.Scope("PROGRAM", null, null, null, program.getId(), program.getProgramName(),
                    program.getDepartment().getId(), program.getDepartment().getDepartmentName());
        }
        if (d.getDepartment() != null) {
            Department department = d.getDepartment();
            return new KnowledgeDocumentResponse.Scope("DEPARTMENT", null, null, null, null, null,
                    department.getId(), department.getDepartmentName());
        }
        return new KnowledgeDocumentResponse.Scope("UNIVERSITY", null, null, null, null, null, null, null);
    }

    private Instant instant(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC); }
}
