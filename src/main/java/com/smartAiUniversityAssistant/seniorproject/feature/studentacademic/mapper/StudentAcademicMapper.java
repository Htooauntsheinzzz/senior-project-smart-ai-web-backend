package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.mapper;

import com.smartAiUniversityAssistant.seniorproject.feature.course.enums.CourseType;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.entity.CourseSectionMeeting;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.entity.Enrollment;
import com.smartAiUniversityAssistant.seniorproject.feature.semester.entity.Semester;
import com.smartAiUniversityAssistant.seniorproject.feature.student.entity.Student;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.entity.TimetableMeetingException;
import java.time.DayOfWeek;
import java.util.List;
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
                s.getAccountStatus(), integer(s.getStudyYear()), program == null ? null : program.getDegreeLevel());
    }

    // Course, Semester and Lecturer always come from the Enrollment's Course Section, never from the Student.
    public StudentAcademicEnrollmentResponse enrollment(Enrollment e, List<StudentCourseMeetingResponse> meetings) {
        var section = e.getCourseSection();
        var course = section.getCourse();
        var semester = section.getSemester();
        var lecture = section.getLecture();
        return new StudentAcademicEnrollmentResponse(e.getId(), e.getEnrollmentDate(), e.getStatus().name(),
                course.getId(), course.getCourseCode(), course.getCourseName(), course.getCreditHours(),
                integer(course.getCourseType()), section.getId(), section.getSectionNumber(),
                semester.getId(), semester.getAcademicYear(), semester.getSemesterNameEn(), semester.getSemesterNameTh(),
                lecture.getId(), lecture.getLectureNameEn(), lecture.getLectureNameTh(), meetings, course.getDescription());
    }

    public StudentAcademicCourseResponse course(Enrollment e, List<StudentCourseMeetingResponse> meetings) {
        var section = e.getCourseSection();
        var course = section.getCourse();
        var semester = section.getSemester();
        var lecture = section.getLecture();
        return new StudentAcademicCourseResponse(course.getId(), course.getCourseCode(), course.getCourseName(),
                course.getCreditHours(), course.getDescription(), integer(course.getCourseType()),
                CourseType.fromCode(course.getCourseType()).getDisplayName(),
                section.getId(), section.getSectionNumber(),
                semester.getId(), semester.getAcademicYear(), semester.getSemesterNameEn(), semester.getSemesterNameTh(),
                e.getStatus().name(), lecture.getId(), lecture.getLectureNameEn(), lecture.getLectureNameTh(), meetings);
    }

    public StudentCourseMeetingResponse meeting(CourseSectionMeeting m, List<TimetableMeetingException> exceptions) {
        var room = m.getRoom();
        var building = room == null ? null : room.getBuilding();
        return new StudentCourseMeetingResponse(m.getMeetingType().name(), DayOfWeek.of(m.getDayOfWeek()).name(),
                m.getStartTime(), m.getEndTime(), m.getEffectiveFrom(), m.getEffectiveTo(),
                room == null ? m.getRoomLabel() : room.getRoomNumber(), m.getId(), m.getDayOfWeek().intValue(),
                room == null ? null : room.getId(), room == null ? null : room.getRoomNumber(),
                building == null ? null : building.getId(), building == null ? null : building.getBuildingName(),
                building == null ? null : building.getSite().getId(), exceptions.stream().map(x -> exception(x, m)).toList());
    }

    public StudentMeetingExceptionResponse exception(TimetableMeetingException x, CourseSectionMeeting m) {
        var room = x.getReplacementRoom() != null ? x.getReplacementRoom() : x.getReplacementDate() != null ? m.getRoom() : null;
        return new StudentMeetingExceptionResponse(x.getOriginalDate(), x.getExceptionType().name(), x.getReplacementDate(),
                x.getReplacementStartTime(), x.getReplacementEndTime(), room == null ? null : room.getId(),
                room == null ? null : room.getRoomNumber(), room == null ? null : room.getBuilding().getBuildingName(), x.getReason());
    }

    public StudentAcademicScheduleResponse schedule(Enrollment e, List<StudentCourseMeetingResponse> meetings) {
        var section = e.getCourseSection();
        var course = section.getCourse();
        var semester = section.getSemester();
        var lecture = section.getLecture();
        return new StudentAcademicScheduleResponse(e.getId(), course.getId(), course.getCourseCode(),
                course.getCourseName(), section.getId(), section.getSectionNumber(),
                semester.getId(), semester.getAcademicYear(), semester.getSemesterNameEn(), semester.getSemesterNameTh(),
                lecture.getId(), lecture.getLectureNameEn(),
                lecture.getLectureNameTh(), e.getStatus().name(), meetings);
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
