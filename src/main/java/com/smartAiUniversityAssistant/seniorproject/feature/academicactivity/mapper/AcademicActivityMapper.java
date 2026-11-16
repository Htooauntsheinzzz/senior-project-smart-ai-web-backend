package com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.mapper;

import com.smartAiUniversityAssistant.seniorproject.config.StudentAcademicProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.entity.*;
import java.time.*;
import org.springframework.stereotype.Component;

// Date-times leave the API in the university's timezone, whatever offset they were stored from.
@Component
public class AcademicActivityMapper {
    private final StudentAcademicProperties properties;
    public AcademicActivityMapper(StudentAcademicProperties properties) { this.properties = properties; }

    public CourseAssignmentResponse assignment(CourseAssignment a) {
        var section = a.getCourseSection();
        var course = section.getCourse();
        return new CourseAssignmentResponse(a.getId(), section.getId(), section.getSectionNumber(), course.getId(),
                course.getCourseCode(), course.getCourseName(), a.getTitle(), a.getDescription(), local(a.getDueAt()),
                a.getStatus().name(), a.getCreatedBy(), instant(a.getCreatedAt()), a.getUpdatedBy(), instant(a.getUpdatedAt()));
    }

    public CourseExamResponse exam(CourseExam x) {
        var section = x.getCourseSection();
        var course = section.getCourse();
        return new CourseExamResponse(x.getId(), section.getId(), section.getSectionNumber(), course.getId(),
                course.getCourseCode(), course.getCourseName(), x.getTitle(), x.getExamType().name(), local(x.getStartsAt()),
                local(x.getEndsAt()), x.getStatus().name(), x.getRoomId(), x.getRoomLabel(), x.getCreatedBy(),
                instant(x.getCreatedAt()), x.getUpdatedBy(), instant(x.getUpdatedAt()));
    }

    private OffsetDateTime local(OffsetDateTime value) { return value.atZoneSameInstant(properties.timezone()).toOffsetDateTime(); }
    private static Instant instant(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC); }
}
