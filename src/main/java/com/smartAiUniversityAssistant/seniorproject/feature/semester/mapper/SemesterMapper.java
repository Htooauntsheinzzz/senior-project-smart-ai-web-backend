package com.smartAiUniversityAssistant.seniorproject.feature.semester.mapper;

import com.smartAiUniversityAssistant.seniorproject.feature.semester.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.semester.entity.Semester;
import java.time.*;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class SemesterMapper {
    public SemesterResponse response(Semester s) {
        return new SemesterResponse(s.getId(), s.getSemesterNameTh(), s.getSemesterNameEn(),
                s.getCreatedBy(), instant(s.getCreatedAt()), s.getUpdatedBy(), instant(s.getUpdatedAt()));
    }

    public SemesterListResponse listResponse(Semester s) {
        return new SemesterListResponse(s.getId(), s.getSemesterNameTh(), s.getSemesterNameEn(),
                instant(s.getCreatedAt()), instant(s.getUpdatedAt()));
    }

    public SemesterPageResponse page(Page<Semester> page, SemesterListQuery query) {
        return new SemesterPageResponse(page.getContent().stream().map(this::listResponse).toList(),
                query.page(), query.size(), page.getTotalElements(), page.getTotalPages(),
                query.page() == 0, query.page() >= page.getTotalPages() - 1, query.sort());
    }

    private Instant instant(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC); }
}
