package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.timetable.service.StudentTimetableService;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.entity.CourseSectionMeeting;
import java.time.*;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.entity.Enrollment;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.enums.EnrollmentStatus;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.repository.EnrollmentRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.student.entity.Student;
import com.smartAiUniversityAssistant.seniorproject.feature.student.enums.StudentAccountStatus;
import com.smartAiUniversityAssistant.seniorproject.feature.student.exception.*;
import com.smartAiUniversityAssistant.seniorproject.feature.student.repository.*;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.mapper.StudentAcademicMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service.StudentAcademicService;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentAcademicServiceImpl implements StudentAcademicService {
    // Request-only convenience; not an EnrollmentStatus value.
    private static final String ALL = "ALL";

    private final StudentRepository students;
    private final StudentCredentialRepository credentials;
    private final EnrollmentRepository enrollments;
    private final StudentTimetableService timetable;
    private final StudentAcademicMapper mapper;
    private final Clock clock;
    private final com.smartAiUniversityAssistant.seniorproject.config.StudentAcademicProperties properties;

    public StudentAcademicServiceImpl(StudentRepository students, StudentCredentialRepository credentials,
            EnrollmentRepository enrollments, StudentTimetableService timetable, StudentAcademicMapper mapper, Clock clock,
            com.smartAiUniversityAssistant.seniorproject.config.StudentAcademicProperties properties) {
        this.students = students;
        this.credentials = credentials;
        this.enrollments = enrollments;
        this.timetable = timetable;
        this.mapper = mapper;
        this.clock = clock;
        this.properties = properties;
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public StudentAcademicProfileResponse getProfile(long studentId) {
        return mapper.profile(findCurrentActiveStudent(studentId));
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public List<StudentAcademicEnrollmentResponse> getEnrollments(long studentId, String status) {
        return withMeetings(studentId, status, mapper::enrollment);
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public List<StudentAcademicCourseResponse> getCourses(long studentId, String status) {
        return withMeetings(studentId, status, mapper::course);
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public List<StudentAcademicScheduleResponse> getSchedule(long studentId, String status) {
        return withMeetings(studentId, status, mapper::schedule);
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public List<StudentAcademicLecturerResponse> getLecturers(long studentId, String status) {
        return academicEnrollments(studentId, status, mapper::lecturer);
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public void requireEligibleStudent(long studentId) {
        findCurrentActiveStudent(studentId);
    }

    // Shared by every list view so filtering, ordering and eligibility stay identical.
    private <T> List<T> academicEnrollments(long studentId, String status, Function<Enrollment, T> view) {
        var statuses = parseStatuses(status);
        findCurrentActiveStudent(studentId);
        return enrollments.findStudentAcademicEnrollments(studentId, statuses).stream().map(view).toList();
    }

    // Two extra queries load the published meetings of every listed section and their upcoming exceptions at once.
    private <T> List<T> withMeetings(long studentId, String status,
            BiFunction<Enrollment, List<StudentCourseMeetingResponse>, T> view) {
        var statuses = parseStatuses(status);
        findCurrentActiveStudent(studentId);
        List<Enrollment> rows = enrollments.findStudentAcademicEnrollments(studentId, statuses);
        if (rows.isEmpty()) return List.of();
        Map<Long, List<StudentCourseMeetingResponse>> bySection = new HashMap<>();
        List<CourseSectionMeeting> published = timetable.publishedMeetings(rows.stream().map(e -> e.getCourseSection().getId()).collect(Collectors.toSet()));
        var exceptions = timetable.upcomingExceptions(published.stream().map(CourseSectionMeeting::getId).toList(),
                LocalDate.ofInstant(clock.instant(), properties.timezone()));
        published.forEach(m -> bySection.computeIfAbsent(m.getCourseSection().getId(), id -> new ArrayList<>())
                .add(mapper.meeting(m, exceptions.getOrDefault(m.getId(), List.of()))));
        return rows.stream().map(e -> view.apply(e, bySection.getOrDefault(e.getCourseSection().getId(), List.of()))).toList();
    }

    private Student findCurrentActiveStudent(long studentId) {
        Student student = students.findByIdAndDeletedFalse(studentId).orElseThrow(StudentNotFoundException::new);
        if (!StudentAccountStatus.ACTIVE.name().equals(student.getAccountStatus()))
            throw new StudentAccountNotActiveException();
        if (!credentials.existsByStudentId(studentId)) throw new InvalidStudentAccountStateException();
        return student;
    }

    private static Set<EnrollmentStatus> parseStatuses(String value) {
        if (value != null && ALL.equals(value.trim().toUpperCase(Locale.ROOT)))
            return EnumSet.allOf(EnrollmentStatus.class);
        return EnumSet.of(EnrollmentStatus.parse(value));
    }
}
