package com.smartAiUniversityAssistant.seniorproject.feature.curriculum.mapper;

import com.smartAiUniversityAssistant.seniorproject.feature.curriculum.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.curriculum.entity.CurriculumVersion;
import com.smartAiUniversityAssistant.seniorproject.feature.program.entity.Program;
import java.time.*;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class CurriculumVersionMapper {
    public CurriculumResponse response(CurriculumVersion c, long documentCount) {
        Program program = c.getProgram();
        return new CurriculumResponse(c.getId(), program.getId(), program.getProgramCode(), program.getProgramName(),
                program.getDepartment().getId(), program.getDepartment().getDepartmentName(),
                c.getVersionCode(), c.getVersionName(), c.getAcademicYearLabel(), c.getDescription(),
                c.getEffectiveFrom(), c.getEffectiveTo(), c.getStatus().name(), documentCount,
                c.getCreatedBy(), instant(c.getCreatedAt()), c.getUpdatedBy(), instant(c.getUpdatedAt()));
    }

    public CurriculumPageResponse page(Page<CurriculumVersion> page, CurriculumListQuery query, Map<Long, Long> documentCounts) {
        var content = page.getContent().stream().map(c -> new CurriculumListResponse(c.getId(), c.getProgram().getId(),
                c.getProgram().getProgramCode(), c.getProgram().getProgramName(), c.getVersionCode(), c.getVersionName(),
                c.getAcademicYearLabel(), c.getEffectiveFrom(), c.getEffectiveTo(), c.getStatus().name(),
                documentCounts.getOrDefault(c.getId(), 0L), instant(c.getCreatedAt()))).toList();
        return new CurriculumPageResponse(content, query.page(), query.size(), page.getTotalElements(), page.getTotalPages(),
                query.page() == 0, query.page() >= page.getTotalPages() - 1, query.sort());
    }

    private Instant instant(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC); }
}
