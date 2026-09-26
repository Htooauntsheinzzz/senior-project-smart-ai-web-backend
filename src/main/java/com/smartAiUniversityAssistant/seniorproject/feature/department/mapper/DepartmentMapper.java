package com.smartAiUniversityAssistant.seniorproject.feature.department.mapper;

import com.smartAiUniversityAssistant.seniorproject.feature.department.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.department.entity.Department;
import java.time.*;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class DepartmentMapper {
    public DepartmentResponse response(Department d, long programCount) {
        var faculty = d.getFaculty();
        return new DepartmentResponse(d.getId(), d.getDepartmentCode(), d.getDepartmentName(),
                faculty.getId(), faculty.getFacultyCode(), faculty.getFacultyNameEn(), programCount, 0, 0,
                d.isActive(), d.getCreatedBy(), instant(d.getCreatedAt()), d.getUpdatedBy(), instant(d.getUpdatedAt()));
    }

    public DepartmentListResponse listResponse(Department d, long programCount) {
        var faculty = d.getFaculty();
        return new DepartmentListResponse(d.getId(), d.getDepartmentCode(), d.getDepartmentName(),
                faculty.getId(), faculty.getFacultyCode(), faculty.getFacultyNameEn(), programCount, 0, 0, d.isActive());
    }

    public DepartmentPageResponse page(Page<Department> page, DepartmentListQuery query, Map<Long, Long> programCounts) {
        return new DepartmentPageResponse(page.getContent().stream()
                .map(d -> listResponse(d, programCounts.getOrDefault(d.getId(), 0L))).toList(),
                query.page(), query.size(), page.getTotalElements(), page.getTotalPages(),
                query.page() == 0, query.page() >= page.getTotalPages() - 1, query.sort());
    }

    private Instant instant(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC); }
}
