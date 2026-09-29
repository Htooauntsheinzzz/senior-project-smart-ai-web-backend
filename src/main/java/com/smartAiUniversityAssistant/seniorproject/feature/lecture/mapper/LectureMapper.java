package com.smartAiUniversityAssistant.seniorproject.feature.lecture.mapper;

import com.smartAiUniversityAssistant.seniorproject.feature.lecture.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.lecture.entity.Lecture;
import java.time.*;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class LectureMapper {
    public LectureResponse response(Lecture l) {
        var faculty = l.getFaculty();
        var department = l.getDepartment();
        return new LectureResponse(l.getId(), l.getLectureNameTh(), l.getLectureNameEn(), l.getLectureNickname(),
                faculty.getId(), faculty.getFacultyCode(), faculty.getFacultyNameEn(),
                department.getId(), department.getDepartmentCode(), department.getDepartmentName(),
                l.getStatus(), l.getCreatedBy(), instant(l.getCreatedAt()), l.getUpdatedBy(), instant(l.getUpdatedAt()));
    }

    public LectureListResponse listResponse(Lecture l) {
        var faculty = l.getFaculty();
        var department = l.getDepartment();
        return new LectureListResponse(l.getId(), l.getLectureNameTh(), l.getLectureNameEn(), l.getLectureNickname(),
                faculty.getId(), faculty.getFacultyCode(), faculty.getFacultyNameEn(),
                department.getId(), department.getDepartmentCode(), department.getDepartmentName(),
                l.getStatus(), instant(l.getCreatedAt()), instant(l.getUpdatedAt()));
    }

    public LecturePageResponse page(Page<Lecture> page, LectureListQuery query) {
        return new LecturePageResponse(page.getContent().stream().map(this::listResponse).toList(),
                query.page(), query.size(), page.getTotalElements(), page.getTotalPages(),
                query.page() == 0, query.page() >= page.getTotalPages() - 1, query.sort());
    }

    private Instant instant(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC); }
}
