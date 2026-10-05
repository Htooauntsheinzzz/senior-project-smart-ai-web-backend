package com.smartAiUniversityAssistant.seniorproject.feature.coursesection.mapper;

import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.entity.CourseSection;
import java.time.*;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class CourseSectionMapper {
    // enrolledCount/isFull stay derived zeros until the Enrollment feature exists.
    public CourseSectionResponse response(CourseSection s) {
        var course = s.getCourse();
        var lecture = s.getLecture();
        var semester = s.getSemester();
        return new CourseSectionResponse(s.getId(),
                course.getId(), course.getCourseCode(), course.getCourseName(),
                s.getSectionNumber(), s.getCapacity(), 0, false,
                lecture.getId(), lecture.getLectureNameTh(), lecture.getLectureNameEn(), lecture.getLectureNickname(),
                s.getRoom(), s.getSchedule(),
                semester.getId(), semester.getSemesterNameTh(), semester.getSemesterNameEn(),
                s.getStatus(), s.getCreatedBy(), instant(s.getCreatedAt()), s.getUpdatedBy(), instant(s.getUpdatedAt()));
    }

    public CourseSectionListResponse listResponse(CourseSection s) {
        var course = s.getCourse();
        var lecture = s.getLecture();
        var semester = s.getSemester();
        return new CourseSectionListResponse(s.getId(),
                course.getId(), course.getCourseCode(), course.getCourseName(),
                s.getSectionNumber(),
                lecture.getId(), lecture.getLectureNameEn(), lecture.getLectureNameTh(),
                s.getRoom(), s.getSchedule(),
                s.getCapacity(), 0, false,
                semester.getId(), semester.getSemesterNameEn(), semester.getSemesterNameTh(),
                s.getStatus(), instant(s.getCreatedAt()), instant(s.getUpdatedAt()));
    }

    public CourseSectionPageResponse page(Page<CourseSection> page, CourseSectionListQuery query) {
        return new CourseSectionPageResponse(page.getContent().stream().map(this::listResponse).toList(),
                query.page(), query.size(), page.getTotalElements(), page.getTotalPages(),
                query.page() == 0, query.page() >= page.getTotalPages() - 1, query.sort());
    }

    private Instant instant(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC); }
}
