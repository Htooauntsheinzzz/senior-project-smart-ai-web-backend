package com.smartAiUniversityAssistant.seniorproject.feature.assistant.mapper;

import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.dto.*;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * Reduces academic query results to the fields the model needs to answer. Internal ids (student, enrollment, course,
 * semester, lecturer, faculty, department, program) are dropped; courseSectionId stays because
 * get_section_availability takes it. Null values are kept as null so the model can say a value is unavailable.
 */
@Component
public class AcademicAiToolResultMapper {

    public Map<String, Object> profile(AcademicProfileQueryResult r) {
        return fields("studentCode", r.studentCode(), "fullName", r.fullName(),
                "facultyCode", r.facultyCode(), "facultyNameEn", r.facultyNameEn(), "facultyNameTh", r.facultyNameTh(),
                "departmentCode", r.departmentCode(), "departmentName", r.departmentName(),
                "programCode", r.programCode(), "programName", r.programName(),
                "semesterNameEn", r.semesterNameEn(), "semesterNameTh", r.semesterNameTh(),
                "semesterAcademicYear", r.semesterAcademicYear(),
                "academicYear", r.academicYear(), "enrollmentYear", r.enrollmentYear());
    }

    public Map<String, Object> enrollment(AcademicEnrollmentQueryResult r) {
        return fields("courseCode", r.courseCode(), "courseName", r.courseName(), "creditHours", r.creditHours(),
                "courseSectionId", r.courseSectionId(), "sectionNumber", r.sectionNumber(),
                "enrollmentStatus", r.enrollmentStatus(),
                "semesterNameEn", r.semesterNameEn(), "semesterNameTh", r.semesterNameTh(),
                "semesterAcademicYear", r.semesterAcademicYear(),
                "lecturerNameEn", r.lecturerNameEn(), "lecturerNameTh", r.lecturerNameTh(),
                "room", r.room(), "schedule", r.schedule());
    }

    public Map<String, Object> course(AcademicCourseQueryResult r) {
        return fields("courseCode", r.courseCode(), "courseName", r.courseName(), "creditHours", r.creditHours(),
                "description", r.description(), "courseTypeName", r.courseTypeName(),
                "courseSectionId", r.courseSectionId(), "sectionNumber", r.sectionNumber(),
                "semesterNameEn", r.semesterNameEn(), "semesterNameTh", r.semesterNameTh(),
                "semesterAcademicYear", r.semesterAcademicYear());
    }

    public Map<String, Object> schedule(AcademicScheduleQueryResult r) {
        return fields("courseCode", r.courseCode(), "courseName", r.courseName(),
                "courseSectionId", r.courseSectionId(), "sectionNumber", r.sectionNumber(),
                "semesterNameEn", r.semesterNameEn(), "semesterNameTh", r.semesterNameTh(),
                "semesterAcademicYear", r.semesterAcademicYear(),
                "schedule", r.schedule(), "room", r.room(),
                "lecturerNameEn", r.lecturerNameEn(), "lecturerNameTh", r.lecturerNameTh());
    }

    public Map<String, Object> courseSection(AcademicCourseSectionQueryResult r) {
        return fields("courseCode", r.courseCode(), "courseName", r.courseName(),
                "courseSectionId", r.courseSectionId(), "sectionNumber", r.sectionNumber(),
                "semesterNameEn", r.semesterNameEn(), "semesterNameTh", r.semesterNameTh(),
                "semesterAcademicYear", r.semesterAcademicYear(),
                "room", r.room(), "schedule", r.schedule(),
                "lecturerNameEn", r.lecturerNameEn(), "lecturerNameTh", r.lecturerNameTh(),
                "enrollmentStatus", r.enrollmentStatus());
    }

    public Map<String, Object> lecturer(AcademicLecturerQueryResult r) {
        return fields("lecturerNameEn", r.lecturerNameEn(), "lecturerNameTh", r.lecturerNameTh(),
                "lecturerNickname", r.lecturerNickname(),
                "courseCode", r.courseCode(), "courseName", r.courseName(),
                "courseSectionId", r.courseSectionId(), "sectionNumber", r.sectionNumber(),
                "semesterNameEn", r.semesterNameEn(), "semesterNameTh", r.semesterNameTh(),
                "semesterAcademicYear", r.semesterAcademicYear());
    }

    public Map<String, Object> courseInformation(CourseInformationQueryResult r) {
        return fields("courseCode", r.courseCode(), "courseName", r.courseName(), "creditHours", r.creditHours(),
                "description", r.description(), "courseTypeName", r.courseTypeName(),
                "prerequisiteCourses", r.prerequisiteCourses(),
                "maximumStudentsPerSection", r.maximumStudentsPerSection(),
                "recommendedAcademicYear", r.recommendedAcademicYear(), "active", r.active(),
                "facultyCode", r.facultyCode(), "facultyNameEn", r.facultyNameEn(), "facultyNameTh", r.facultyNameTh(),
                "departmentCode", r.departmentCode(), "departmentName", r.departmentName(),
                "programCode", r.programCode(), "programName", r.programName());
    }

    public Map<String, Object> sectionAvailability(SectionAvailabilityQueryResult r) {
        return fields("courseSectionId", r.courseSectionId(), "courseCode", r.courseCode(), "courseName", r.courseName(),
                "sectionNumber", r.sectionNumber(), "capacity", r.capacity(), "occupiedSeats", r.occupiedSeats(),
                "availableSeats", r.availableSeats(), "isFull", r.isFull(), "sectionStatus", r.sectionStatus(),
                "semesterNameEn", r.semesterNameEn(), "semesterNameTh", r.semesterNameTh(),
                "semesterAcademicYear", r.semesterAcademicYear());
    }

    public List<Map<String, Object>> candidates(List<AcademicCourseCandidate> candidates) {
        return candidates.stream().map(c -> fields("courseCode", c.courseCode(), "courseName", c.courseName())).toList();
    }

    // LinkedHashMap keeps null values and a stable field order.
    private static Map<String, Object> fields(Object... pairs) {
        var map = new LinkedHashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], pairs[i + 1]);
        return map;
    }
}
