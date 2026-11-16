package com.smartAiUniversityAssistant.seniorproject.feature.academicresult.mapper;

import com.smartAiUniversityAssistant.seniorproject.feature.academicresult.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.academicresult.entity.StudentCourseResult;
import java.time.*;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class CourseResultMapper {
    public CourseResultResponse response(StudentCourseResult r) {
        var e = r.getEnrollment();
        var section = e.getCourseSection();
        var course = section.getCourse();
        var grade = r.getGradeDefinition();
        var scheme = grade.getGradingScheme();
        return new CourseResultResponse(r.getId(), e.getId(), e.getStudent().getId(), e.getStudent().getStudentCode(),
                course.getId(), course.getCourseCode(), course.getCourseName(), section.getId(), section.getSectionNumber(),
                section.getSemester().getId(), scheme.getId(), scheme.getSchemeCode(), grade.getId(), grade.getGradeCode(),
                grade.getGradePoint(), r.getCreditsAttempted(), r.getStatus().name(),
                instant(r.getFinalizedAt()), r.getFinalizedBy(), r.getCreatedBy(), instant(r.getCreatedAt()),
                r.getUpdatedBy(), instant(r.getUpdatedAt()));
    }

    public CourseResultPageResponse page(Page<StudentCourseResult> page, CourseResultListQuery query) {
        return new CourseResultPageResponse(page.getContent().stream().map(this::response).toList(), query.page(),
                query.size(), page.getTotalElements(), page.getTotalPages(), query.page() == 0,
                query.page() >= page.getTotalPages() - 1);
    }

    private static Instant instant(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC); }
}
