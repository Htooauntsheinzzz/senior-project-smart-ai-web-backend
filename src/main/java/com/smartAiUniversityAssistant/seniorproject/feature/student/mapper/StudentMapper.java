package com.smartAiUniversityAssistant.seniorproject.feature.student.mapper;

import com.smartAiUniversityAssistant.seniorproject.feature.department.entity.Department;
import com.smartAiUniversityAssistant.seniorproject.feature.faculty.entity.Faculty;
import com.smartAiUniversityAssistant.seniorproject.feature.program.entity.Program;
import com.smartAiUniversityAssistant.seniorproject.feature.semester.entity.Semester;
import com.smartAiUniversityAssistant.seniorproject.feature.student.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.student.entity.*;
import java.time.*;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

// Faculty, department and program are null for a self-registered student who has not chosen them yet.
@Component
public class StudentMapper {
    public StudentResponse response(Student s, StudentCredential credential) {
        Faculty f = s.getFaculty();
        Department d = s.getDepartment();
        Program p = s.getProgram();
        Semester m = s.getSemester();
        return new StudentResponse(s.getId(), s.getStudentCode(), s.getUniversityEmail(),
                s.getFirstName(), s.getLastName(), s.getPhoneNumber(), s.getDateOfBirth(),
                get(f, Faculty::getId), get(f, Faculty::getFacultyCode), get(f, Faculty::getFacultyNameEn),
                get(d, Department::getId), get(d, Department::getDepartmentCode), get(d, Department::getDepartmentName),
                get(p, Program::getId), get(p, Program::getProgramCode), get(p, Program::getProgramName),
                get(m, Semester::getId), get(m, Semester::getSemesterNameTh), get(m, Semester::getSemesterNameEn),
                integer(s.getAcademicYear()), integer(s.getEnrollmentYear()),
                s.getAccountStatus(), credential != null && credential.isForcePasswordChange(),
                s.getCreatedBy(), instant(s.getCreatedAt()), s.getUpdatedBy(), instant(s.getUpdatedAt()));
    }

    public StudentListResponse listResponse(Student s) {
        Faculty f = s.getFaculty();
        Department d = s.getDepartment();
        Program p = s.getProgram();
        Semester m = s.getSemester();
        return new StudentListResponse(s.getId(), s.getStudentCode(), s.getUniversityEmail(),
                s.getFirstName(), s.getLastName(),
                get(f, Faculty::getId), get(f, Faculty::getFacultyCode), get(f, Faculty::getFacultyNameEn),
                get(d, Department::getId), get(d, Department::getDepartmentCode), get(d, Department::getDepartmentName),
                get(p, Program::getId), get(p, Program::getProgramCode), get(p, Program::getProgramName),
                get(m, Semester::getId), get(m, Semester::getSemesterNameEn),
                integer(s.getAcademicYear()), integer(s.getEnrollmentYear()),
                s.getAccountStatus(), instant(s.getCreatedAt()), instant(s.getUpdatedAt()));
    }

    public StudentPageResponse page(Page<Student> page, StudentListQuery query) {
        return new StudentPageResponse(page.getContent().stream().map(this::listResponse).toList(),
                query.page(), query.size(), page.getTotalElements(), page.getTotalPages(),
                query.page() == 0, query.page() >= page.getTotalPages() - 1, query.sort());
    }

    public StudentMeResponse me(Student s, StudentCredential credential) {
        Faculty f = s.getFaculty();
        Department d = s.getDepartment();
        Program p = s.getProgram();
        Semester m = s.getSemester();
        return new StudentMeResponse(s.getId(), s.getStudentCode(), s.getUniversityEmail(),
                s.getFirstName(), s.getLastName(), s.getPhoneNumber(), s.getDateOfBirth(),
                get(f, Faculty::getId), get(f, Faculty::getFacultyCode), get(f, Faculty::getFacultyNameEn),
                get(d, Department::getId), get(d, Department::getDepartmentCode), get(d, Department::getDepartmentName),
                get(p, Program::getId), get(p, Program::getProgramCode), get(p, Program::getProgramName),
                get(m, Semester::getId), get(m, Semester::getSemesterNameTh), get(m, Semester::getSemesterNameEn),
                integer(s.getAcademicYear()), integer(s.getEnrollmentYear()), s.getAccountStatus(),
                credential != null && credential.isForcePasswordChange());
    }

    public StudentRegisterResponse registerResponse(Student s) {
        return new StudentRegisterResponse(s.getId(), s.getStudentCode(), s.getUniversityEmail(),
                s.getFirstName(), s.getLastName(), s.getAccountStatus());
    }

    public StudentAuthCache authCache(Student s, StudentCredential credential) {
        return new StudentAuthCache(s.getId(), s.getStudentCode(), s.getAccountStatus(), s.isDeleted(),
                credential != null && credential.isForcePasswordChange(),
                credential == null ? null : credential.getLockedUntil());
    }

    private <T, R> R get(T value, Function<T, R> getter) { return value == null ? null : getter.apply(value); }
    private Integer integer(Short value) { return value == null ? null : value.intValue(); }
    private Instant instant(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC); }
}
