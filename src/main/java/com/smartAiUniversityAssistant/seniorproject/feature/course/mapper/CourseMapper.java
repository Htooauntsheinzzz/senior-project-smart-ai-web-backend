package com.smartAiUniversityAssistant.seniorproject.feature.course.mapper;

import com.smartAiUniversityAssistant.seniorproject.feature.course.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.course.entity.Course;
import com.smartAiUniversityAssistant.seniorproject.feature.course.enums.CourseType;
import java.time.*;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class CourseMapper {
    public CourseResponse response(Course c) {
        var faculty = c.getFaculty();
        var department = c.getDepartment();
        var program = c.getProgram();
        var semester = c.getSemester();
        return new CourseResponse(c.getId(), c.getCourseCode(), c.getCourseName(), c.getCreditHours(), c.getDescription(),
                faculty.getId(), faculty.getFacultyCode(), faculty.getFacultyNameEn(),
                department.getId(), department.getDepartmentCode(), department.getDepartmentName(),
                program == null ? null : program.getId(), program == null ? null : program.getProgramCode(),
                program == null ? null : program.getProgramName(),
                semester == null ? null : semester.getId(), semester == null ? null : semester.getSemesterNameTh(),
                semester == null ? null : semester.getSemesterNameEn(),
                integer(c.getRecommendedAcademicYear()), c.getPrerequisiteCourses(),
                integer(c.getCourseType()), typeName(c), c.getMaximumStudentsPerSection(),
                c.isActive(), c.getCreatedBy(), instant(c.getCreatedAt()), c.getUpdatedBy(), instant(c.getUpdatedAt()));
    }

    public CourseListResponse listResponse(Course c, long sectionCount) {
        var faculty = c.getFaculty();
        var department = c.getDepartment();
        var program = c.getProgram();
        var semester = c.getSemester();
        return new CourseListResponse(c.getId(), c.getCourseCode(), c.getCourseName(), c.getCreditHours(),
                faculty.getId(), faculty.getFacultyCode(), faculty.getFacultyNameEn(),
                department.getId(), department.getDepartmentCode(), department.getDepartmentName(),
                program == null ? null : program.getId(), program == null ? null : program.getProgramCode(),
                program == null ? null : program.getProgramName(),
                semester == null ? null : semester.getId(), semester == null ? null : semester.getSemesterNameTh(),
                semester == null ? null : semester.getSemesterNameEn(),
                integer(c.getRecommendedAcademicYear()), integer(c.getCourseType()), typeName(c),
                c.getMaximumStudentsPerSection(), (int) sectionCount, c.isActive(),
                instant(c.getCreatedAt()), instant(c.getUpdatedAt()));
    }

    public CoursePageResponse page(Page<Course> page, CourseListQuery query, java.util.Map<Long, Long> sectionCounts) {
        return new CoursePageResponse(page.getContent().stream()
                .map(c -> listResponse(c, sectionCounts.getOrDefault(c.getId(), 0L))).toList(),
                query.page(), query.size(), page.getTotalElements(), page.getTotalPages(),
                query.page() == 0, query.page() >= page.getTotalPages() - 1, query.sort());
    }

    private String typeName(Course c) { return CourseType.fromCode(c.getCourseType()).getDisplayName(); }
    private Integer integer(Short value) { return value == null ? null : value.intValue(); }
    private Instant instant(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC); }
}
