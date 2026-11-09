package com.smartAiUniversityAssistant.seniorproject.feature.academicquery.mapper;

import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.course.entity.Course;
import com.smartAiUniversityAssistant.seniorproject.feature.course.enums.CourseType;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.entity.CourseSection;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto.*;
import java.util.List;
import org.springframework.stereotype.Component;

// Structured values only: the assistant writes the natural-language answer, never this mapper.
@Component
public class AcademicQueryMapper {
    public AcademicProfileQueryResult toProfileResult(StudentAcademicProfileResponse p) {
        var faculty = p.faculty();
        var department = p.department();
        var program = p.program();
        var semester = p.semester();
        return new AcademicProfileQueryResult(p.studentId(), p.studentCode(), p.fullName(), p.universityEmail(),
                faculty == null ? null : faculty.id(), faculty == null ? null : faculty.code(),
                faculty == null ? null : faculty.nameEn(), faculty == null ? null : faculty.nameTh(),
                department == null ? null : department.id(), department == null ? null : department.code(),
                department == null ? null : department.nameEn(),
                program == null ? null : program.id(), program == null ? null : program.code(),
                program == null ? null : program.nameEn(),
                semester == null ? null : semester.id(), semester == null ? null : semester.academicYear(),
                semester == null ? null : semester.semesterNameEn(), semester == null ? null : semester.semesterNameTh(),
                p.academicYear(), p.enrollmentYear());
    }

    public AcademicEnrollmentQueryResult toEnrollmentResult(StudentAcademicEnrollmentResponse e) {
        return new AcademicEnrollmentQueryResult(e.enrollmentId(), e.enrollmentStatus(), e.enrollmentDate(),
                e.courseId(), e.courseCode(), e.courseName(), e.creditHours(), e.courseSectionId(), e.sectionNumber(),
                e.semesterId(), e.semesterAcademicYear(), e.semesterNameEn(), e.semesterNameTh(),
                e.lecturerId(), e.lecturerNameEn(), e.lecturerNameTh(), meetings(e.meetings()));
    }

    public AcademicCourseQueryResult toCourseResult(StudentAcademicCourseResponse c) {
        return new AcademicCourseQueryResult(c.courseId(), c.courseCode(), c.courseName(), c.creditHours(),
                c.description(), c.courseType(), c.courseTypeName(), c.courseSectionId(), c.sectionNumber(),
                c.semesterId(), c.semesterAcademicYear(), c.semesterNameEn(), c.semesterNameTh());
    }

    public AcademicScheduleQueryResult toScheduleResult(StudentAcademicScheduleResponse s) {
        return new AcademicScheduleQueryResult(s.courseId(), s.courseCode(), s.courseName(), s.courseSectionId(),
                s.sectionNumber(), s.semesterId(), s.semesterAcademicYear(), s.semesterNameEn(), s.semesterNameTh(),
                s.lecturerId(), s.lecturerNameEn(), s.lecturerNameTh(), meetings(s.meetings()));
    }

    // Published weekly meetings (spec 40) replace the former free-text room and schedule of a section.
    private static List<AcademicMeetingQueryResult> meetings(List<StudentCourseMeetingResponse> meetings) {
        return meetings == null ? List.of() : meetings.stream().map(m -> new AcademicMeetingQueryResult(m.meetingType(),
                m.dayOfWeek(), m.startTime(), m.endTime(), m.effectiveFrom(), m.effectiveTo(), m.roomName(), m.buildingName())).toList();
    }

    public AcademicCourseSectionQueryResult toSectionResult(StudentAcademicEnrollmentResponse e) {
        return new AcademicCourseSectionQueryResult(e.courseId(), e.courseCode(), e.courseName(),
                e.courseSectionId(), e.sectionNumber(),
                e.semesterId(), e.semesterAcademicYear(), e.semesterNameEn(), e.semesterNameTh(),
                e.lecturerId(), e.lecturerNameEn(), e.lecturerNameTh(), e.enrollmentStatus(), meetings(e.meetings()));
    }

    public AcademicLecturerQueryResult toLecturerResult(StudentAcademicLecturerResponse l) {
        return new AcademicLecturerQueryResult(l.lecturerId(), l.lecturerNameEn(), l.lecturerNameTh(),
                l.lecturerNickname(), l.courseId(), l.courseCode(), l.courseName(), l.courseSectionId(),
                l.sectionNumber(), l.semesterId(), l.semesterAcademicYear(), l.semesterNameEn(), l.semesterNameTh());
    }

    public CourseInformationQueryResult toCourseInformationResult(Course c) {
        var faculty = c.getFaculty();
        var department = c.getDepartment();
        var program = c.getProgram();
        return new CourseInformationQueryResult(c.getId(), c.getCourseCode(), c.getCourseName(), c.getCreditHours(),
                c.getDescription(), integer(c.getCourseType()), CourseType.fromCode(c.getCourseType()).getDisplayName(),
                c.getPrerequisiteCourses(), c.getMaximumStudentsPerSection(), integer(c.getRecommendedAcademicYear()),
                c.isActive(), faculty.getId(), faculty.getFacultyCode(), faculty.getFacultyNameEn(),
                faculty.getFacultyNameTh(), department.getId(), department.getDepartmentCode(),
                department.getDepartmentName(), program == null ? null : program.getId(),
                program == null ? null : program.getProgramCode(), program == null ? null : program.getProgramName());
    }

    public SectionAvailabilityQueryResult toAvailabilityResult(CourseSection s, long occupiedSeats) {
        var course = s.getCourse();
        var semester = s.getSemester();
        int capacity = s.getCapacity();
        return new SectionAvailabilityQueryResult(s.getId(), course.getId(), course.getCourseCode(),
                course.getCourseName(), s.getSectionNumber(), capacity, occupiedSeats,
                Math.max(0, capacity - occupiedSeats), occupiedSeats >= capacity, s.getStatus(),
                semester.getId(), semester.getAcademicYear(), semester.getSemesterNameEn(), semester.getSemesterNameTh());
    }

    public AcademicCourseCandidate toCandidate(Course c) {
        return new AcademicCourseCandidate(c.getId(), c.getCourseCode(), c.getCourseName());
    }

    private static Integer integer(Short value) { return value == null ? null : value.intValue(); }
}
