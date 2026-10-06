package com.smartAiUniversityAssistant.seniorproject.feature.student.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.student.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.student.entity.*;
import com.smartAiUniversityAssistant.seniorproject.feature.student.enums.StudentAccountStatus;
import com.smartAiUniversityAssistant.seniorproject.feature.student.exception.*;
import com.smartAiUniversityAssistant.seniorproject.feature.student.mapper.StudentMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.student.repository.*;
import com.smartAiUniversityAssistant.seniorproject.feature.student.service.*;
import java.time.*;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentProfileServiceImpl implements StudentProfileService {
    private final StudentRepository students;
    private final StudentCredentialRepository credentials;
    private final StudentMapper mapper;
    private final StudentCacheService cache;
    private final StudentAcademicValidator academics;
    private final Clock clock;

    public StudentProfileServiceImpl(StudentRepository students, StudentCredentialRepository credentials,
            StudentMapper mapper, StudentCacheService cache, StudentAcademicValidator academics, Clock clock) {
        this.students = students;
        this.credentials = credentials;
        this.mapper = mapper;
        this.cache = cache;
        this.academics = academics;
        this.clock = clock;
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public StudentMeResponse profile(long studentId) {
        var cached = cache.findProfile(studentId);
        if (cached.isPresent()) return cached.get();
        Student student = students.findByIdAndDeletedFalse(studentId).orElseThrow(StudentNotFoundException::new);
        StudentCredential credential = credentials.findByStudentId(studentId).orElse(null);
        var profile = mapper.me(student, credential);
        cache.saveProfile(profile);
        return profile;
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public Optional<StudentAuthCache> authState(long studentId) {
        var cached = cache.findAuth(studentId);
        if (cached.isPresent()) return cached;
        var state = load(studentId);
        state.ifPresent(cache::saveAuth);
        return state;
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public StudentAuthCache requireRefreshEligible(long studentId) {
        Student student = students.findById(studentId).orElseThrow(InvalidRefreshTokenException::new);
        if (student.isDeleted()) throw new InvalidRefreshTokenException();
        if (!StudentAccountStatus.ACTIVE.name().equals(student.getAccountStatus()))
            throw new StudentAccountNotActiveException();
        StudentCredential credential = credentials.findByStudentId(studentId)
                .orElseThrow(InvalidStudentAccountStateException::new);
        var state = mapper.authCache(student, credential);
        cache.saveAuth(state);
        return state;
    }

    @Override @Transactional(timeout = 15)
    public StudentMeResponse updateProfile(long studentId, StudentProfileUpdateRequest request) {
        Student student = students.findByIdForUpdate(studentId).orElseThrow(StudentAccountNotActiveException::new);
        if (student.isDeleted() || !StudentAccountStatus.ACTIVE.name().equals(student.getAccountStatus()))
            throw new StudentAccountNotActiveException();
        var placement = academics.validate(request.facultyId(), request.departmentId(), request.programId(),
                request.semesterId(), student);
        student.setFirstName(request.firstName());
        student.setLastName(request.lastName());
        student.setPhoneNumber(request.phoneNumber());
        student.setDateOfBirth(request.dateOfBirth());
        student.setFaculty(placement.faculty());
        student.setDepartment(placement.department());
        student.setProgram(placement.program());
        student.setSemester(placement.semester());
        student.setAcademicYear(request.academicYear() == null ? null : request.academicYear().shortValue());
        student.setEnrollmentYear(request.enrollmentYear() == null ? null : request.enrollmentYear().shortValue());
        student.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        students.flush();
        cache.evictAfterCommit(studentId);
        return mapper.me(student, credentials.findByStudentId(studentId).orElse(null));
    }

    private Optional<StudentAuthCache> load(long studentId) {
        return students.findById(studentId).flatMap(student -> credentials.findByStudentId(studentId)
                .map(credential -> mapper.authCache(student, credential)));
    }
}
