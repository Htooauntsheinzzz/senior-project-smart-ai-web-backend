package com.smartAiUniversityAssistant.seniorproject.feature.program.mapper;

import com.smartAiUniversityAssistant.seniorproject.feature.department.entity.Department;
import com.smartAiUniversityAssistant.seniorproject.feature.faculty.entity.Faculty;
import com.smartAiUniversityAssistant.seniorproject.feature.program.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.program.entity.Program;
import java.time.*;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class ProgramMapper {
    public ProgramResponse response(Program p) {
        Department department = p.getDepartment();
        Faculty faculty = department.getFaculty();
        return new ProgramResponse(p.getId(), p.getProgramCode(), p.getProgramName(), p.getDegreeLevel(),
                faculty.getId(), faculty.getFacultyCode(), faculty.getFacultyNameEn(),
                department.getId(), department.getDepartmentCode(), department.getDepartmentName(),
                p.getDurationYears(), p.getTotalCredits(), p.isActive(),
                p.getCreatedBy(), instant(p.getCreatedAt()), p.getUpdatedBy(), instant(p.getUpdatedAt()));
    }

    public ProgramListResponse listResponse(Program p) {
        Department department = p.getDepartment();
        Faculty faculty = department.getFaculty();
        return new ProgramListResponse(p.getId(), p.getProgramCode(), p.getProgramName(), p.getDegreeLevel(),
                faculty.getId(), faculty.getFacultyCode(), faculty.getFacultyNameEn(),
                department.getId(), department.getDepartmentCode(), department.getDepartmentName(),
                p.getDurationYears(), p.getTotalCredits(), p.isActive());
    }

    public ProgramPageResponse page(Page<Program> page, ProgramListQuery query) {
        return new ProgramPageResponse(page.getContent().stream().map(this::listResponse).toList(),
                query.page(), query.size(), page.getTotalElements(), page.getTotalPages(),
                query.page() == 0, query.page() >= page.getTotalPages() - 1, query.sort());
    }

    private Instant instant(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC); }
}
