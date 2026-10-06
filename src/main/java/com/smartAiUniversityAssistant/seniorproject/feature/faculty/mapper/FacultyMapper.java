package com.smartAiUniversityAssistant.seniorproject.feature.faculty.mapper;

import com.smartAiUniversityAssistant.seniorproject.feature.faculty.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.faculty.entity.Faculty;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class FacultyMapper {
    public FacultyResponse response(Faculty faculty, long departmentCount) {
        return new FacultyResponse(faculty.getId(), faculty.getFacultyCode(), faculty.getFacultyNameEn(),
                faculty.getFacultyNameTh(), faculty.isActive(), departmentCount, 0, faculty.getCreatedBy(),
                instant(faculty.getCreatedAt()), faculty.getUpdatedBy(), instant(faculty.getUpdatedAt()));
    }

    public FacultyListResponse listResponse(Faculty faculty, long departmentCount) {
        return new FacultyListResponse(faculty.getId(), faculty.getFacultyCode(), faculty.getFacultyNameEn(),
                faculty.getFacultyNameTh(), departmentCount, 0, faculty.isActive(), instant(faculty.getCreatedAt()));
    }

    public FacultyPageResponse page(Page<Faculty> page, FacultyListQuery.Parsed query, Map<Long, Long> departmentCounts) {
        List<FacultyListResponse> content = page.getContent().stream()
                .map(faculty -> listResponse(faculty, departmentCounts.getOrDefault(faculty.getId(), 0L))).toList();
        List<String> sort = query.sort().stream()
                .map(order -> order.field() + "," + (order.ascending() ? "asc" : "desc")).toList();
        return new FacultyPageResponse(content, query.page(), query.size(), page.getTotalElements(),
                page.getTotalPages(), query.page() == 0, query.page() >= page.getTotalPages() - 1, sort);
    }

    private Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
