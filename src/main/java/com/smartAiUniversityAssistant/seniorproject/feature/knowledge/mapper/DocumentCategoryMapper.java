package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.mapper;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.entity.DocumentCategory;
import java.time.*;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class DocumentCategoryMapper {
    public DocumentCategoryResponse response(DocumentCategory c, long documentCount) {
        return new DocumentCategoryResponse(c.getId(), c.getCategoryCode(), c.getCategoryName(), c.getDescription(),
                c.isActive(), documentCount, c.getCreatedBy(), instant(c.getCreatedAt()), c.getUpdatedBy(), instant(c.getUpdatedAt()));
    }

    public DocumentCategoryPageResponse page(Page<DocumentCategory> page, DocumentCategoryListQuery query, Map<Long, Long> counts) {
        return new DocumentCategoryPageResponse(page.getContent().stream()
                .map(c -> response(c, counts.getOrDefault(c.getId(), 0L))).toList(),
                query.page(), query.size(), page.getTotalElements(), page.getTotalPages(),
                query.page() == 0, query.page() >= page.getTotalPages() - 1, query.sort());
    }

    private Instant instant(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC); }
}
