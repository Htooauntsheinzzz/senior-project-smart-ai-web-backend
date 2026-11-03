package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.mapper;

import com.smartAiUniversityAssistant.seniorproject.feature.course.enums.CourseType;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.entity.Enrollment;
import com.smartAiUniversityAssistant.seniorproject.feature.semester.entity.Semester;
import com.smartAiUniversityAssistant.seniorproject.feature.student.entity.Student;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto.*;
import org.springframework.stereotype.Component;

@Component
public class StudentAcademicMapper {
    public StudentAcademicProfileResponse profile(Student s) {
        var faculty = s.getFaculty();
        var department = s.getDepartment();
        var program = s.getProgram();
        return new StudentAcademicProfileResponse(s.getId(), s.getStudentCode(), s.getFirstName(), s.getLastName(),
                s.getFirstName() + " " + s.getLastName(), s.getUniversityEmail(), s.getPhoneNumber(), s.getDateOfBirth(),
                faculty == null ? null : new AcademicReferenceResponse(faculty.getId(), faculty.getFacultyCode(),
                        faculty.getFacultyNameEn(), faculty.getFacultyNameTh()),
                department == null ? null : new AcademicReferenceResponse(department.getId(),
                        department.getDepartmentCode(), department.getDepartmentName(), null),
                program == null ? null : new AcademicReferenceResponse(program.getId(), program.getProgramCode(),
                        program.getProgramName(), null),
                semester(s.getSemester()), integer(s.getAcademicYear()), integer(s.getEnrollmentYear()),
                s.getAccountStatus());
    }

    // Course, Semester and Lecturer always come from the Enrollment's Course Section, never from the Student.
    public StudentAcademicEnrollmentResponse enrollment(Enrollment e) {
        var section = e.getCourseSection();
        var course = section.getCourse();
        var semester = section.getSemester();
        var lecture = section.getLecture();
        return new StudentAcademicEnrollmentResponse(e.getId(), e.getEnrollmentDate(), e.getStatus().name(),
                course.getId(), course.getCourseCode(), course.getCourseName(), course.getCreditHours(),
                integer(course.getCourseType()), section.getId(), section.getSectionNumber(),
                semester.getId(), semester.getAcademicYear(), semester.getSemesterNameEn(), semester.getSemesterNameTh(),
                lecture.getId(), lecture.getLectureNameEn(), lecture.getLectureNameTh(),
                section.getRoom(), section.getSchedule());
    }

    public StudentAcademicCourseResponse course(Enrollment e) {
        var section = e.getCourseSection();
        var course = section.getCourse();
        var semester = section.getSemester();
        return new StudentAcademicCourseResponse(course.getId(), course.getCourseCode(), course.getCourseName(),
                course.getCreditHours(), course.getDescription(), integer(course.getCourseType()),
                CourseType.fromCode(course.getCourseType()).getDisplayName(),
                section.getId(), section.getSectionNumber(),
                semester.getId(), semester.getAcademicYear(), semester.getSemesterNameEn(), semester.getSemesterNameTh(),
                e.getStatus().name());
    }

    public StudentAcademicScheduleResponse schedule(Enrollment e) {
        var section = e.getCourseSection();
        var course = section.getCourse();
        var semester = section.getSemester();
        var lecture = section.getLecture();
        return new StudentAcademicScheduleResponse(e.getId(), course.getId(), course.getCourseCode(),
                course.getCourseName(), section.getId(), section.getSectionNumber(),
                semester.getId(), semester.getAcademicYear(), semester.getSemesterNameEn(), semester.getSemesterNameTh(),
                section.getSchedule(), section.getRoom(), lecture.getId(), lecture.getLectureNameEn(),
                lecture.getLectureNameTh(), e.getStatus().name());
    }

    public StudentAcademicLecturerResponse lecturer(Enrollment e) {
        var section = e.getCourseSection();
        var course = section.getCourse();
        var semester = section.getSemester();
        var lecture = section.getLecture();
        return new StudentAcademicLecturerResponse(lecture.getId(), lecture.getLectureNameEn(),
                lecture.getLectureNameTh(), lecture.getLectureNickname(), course.getId(), course.getCourseCode(),
                course.getCourseName(), section.getId(), section.getSectionNumber(),
                semester.getId(), semester.getAcademicYear(), semester.getSemesterNameEn(), semester.getSemesterNameTh());
    }

    private AcademicSemesterResponse semester(Semester semester) {
        return semester == null ? null : new AcademicSemesterResponse(semester.getId(), semester.getAcademicYear(),
                semester.getSemesterNameEn(), semester.getSemesterNameTh());
    }

    private static Integer integer(Short value) { return value == null ? null : value.intValue(); }
}
