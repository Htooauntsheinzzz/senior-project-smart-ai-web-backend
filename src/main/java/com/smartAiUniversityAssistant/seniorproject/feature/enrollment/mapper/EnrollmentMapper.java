package com.smartAiUniversityAssistant.seniorproject.feature.enrollment.mapper;

import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.entity.Enrollment;
import java.time.*;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class EnrollmentMapper {
    // Every display field is derived through Student -> Course Section -> Course/Semester/Lecture.
    public EnrollmentResponse response(Enrollment e, long enrolledCount) {
        var student = e.getStudent();
        var section = e.getCourseSection();
        var course = section.getCourse();
        var semester = section.getSemester();
        var lecture = section.getLecture();
        return new EnrollmentResponse(e.getId(),
                student.getId(), student.getStudentCode(), student.getFirstName(), student.getLastName(),
                student.getUniversityEmail(),
                course.getId(), course.getCourseCode(), course.getCourseName(),
                section.getId(), section.getSectionNumber(),
                semester.getId(), semester.getSemesterNameEn(), semester.getSemesterNameTh(),
                lecture.getId(), lecture.getLectureNameEn(), lecture.getLectureNameTh(),
                section.getRoom(), section.getSchedule(),
                section.getCapacity(), enrolledCount, enrolledCount >= section.getCapacity(),
                e.getEnrollmentDate(), e.getStatus().name(),
                e.getCreatedBy(), instant(e.getCreatedAt()), e.getUpdatedBy(), instant(e.getUpdatedAt()));
    }

    public EnrollmentListResponse listResponse(Enrollment e) {
        var student = e.getStudent();
        var section = e.getCourseSection();
        var course = section.getCourse();
        var semester = section.getSemester();
        return new EnrollmentListResponse(e.getId(),
                student.getId(), student.getStudentCode(), student.getFirstName() + " " + student.getLastName(),
                course.getId(), course.getCourseCode(), course.getCourseName(),
                section.getId(), section.getSectionNumber(),
                e.getEnrollmentDate(),
                semester.getId(), semester.getSemesterNameEn(), semester.getSemesterNameTh(),
                e.getStatus().name(), instant(e.getCreatedAt()), instant(e.getUpdatedAt()));
    }

    public EnrollmentPageResponse page(Page<Enrollment> page, EnrollmentListQuery query) {
        return new EnrollmentPageResponse(page.getContent().stream().map(this::listResponse).toList(),
                query.page(), query.size(), page.getTotalElements(), page.getTotalPages(),
                query.page() == 0, query.page() >= page.getTotalPages() - 1, query.sort());
    }

    private Instant instant(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC); }
}
