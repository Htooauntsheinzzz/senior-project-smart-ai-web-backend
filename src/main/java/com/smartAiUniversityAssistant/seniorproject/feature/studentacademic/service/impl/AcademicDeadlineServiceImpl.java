package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service.impl;

import com.smartAiUniversityAssistant.seniorproject.config.StudentAcademicProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.exception.AcademicActivityFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.repository.*;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.enums.CourseSectionStatus;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.enums.EnrollmentStatus;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.repository.EnrollmentRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service.*;
import java.time.*;
import java.util.*;
import java.util.stream.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcademicDeadlineServiceImpl implements AcademicDeadlineService {
    private static final Duration MICROSECOND = Duration.ofNanos(1000);

    private final StudentAcademicService academics;
    private final EnrollmentRepository enrollments;
    private final CourseAssignmentRepository assignments;
    private final CourseExamRepository exams;
    private final StudentAcademicProperties properties;
    private final Clock clock;

    public AcademicDeadlineServiceImpl(StudentAcademicService academics, EnrollmentRepository enrollments,
            CourseAssignmentRepository assignments, CourseExamRepository exams, StudentAcademicProperties properties, Clock clock) {
        this.academics = academics;
        this.enrollments = enrollments;
        this.assignments = assignments;
        this.exams = exams;
        this.properties = properties;
        this.clock = clock;
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public StudentDeadlinesResponse getDeadlines(long studentId, Integer limit, OffsetDateTime from, OffsetDateTime to) {
        int size = limit == null ? properties.deadlineDefaultLimit() : limit;
        ZonedDateTime now = clock.instant().atZone(properties.timezone());
        OffsetDateTime start = from == null ? now.toOffsetDateTime() : from;
        OffsetDateTime end = to == null ? start.plusDays(properties.deadlineDefaultWindowDays()) : to;
        if (size < 1 || size > properties.deadlineMaxLimit() || end.isBefore(start)
                || end.isAfter(start.plusDays(properties.deadlineMaxWindowDays())))
            throw new AcademicActivityFailure(400, "VALIDATION_ERROR", "The request is invalid.");
        academics.requireEligibleStudent(studentId);
        Set<Long> sections = enrollments.findStudentAcademicEnrollments(studentId, EnumSet.of(EnrollmentStatus.ACTIVE)).stream()
                .map(e -> e.getCourseSection())
                .filter(s -> CourseSectionStatus.ACTIVE.getValue().equals(s.getStatus()))
                .map(s -> s.getId()).collect(Collectors.toSet());
        if (sections.isEmpty()) return new StudentDeadlinesResponse(0, 0, List.of());
        List<StudentDeadlineItemResponse> window = merged(sections, start, end, now);
        // The university's calendar day, whatever window the caller asked for.
        OffsetDateTime tonight = now.toLocalDate().plusDays(1).atStartOfDay(properties.timezone())
                .toOffsetDateTime().minus(MICROSECOND);
        long dueToday = merged(sections, now.toOffsetDateTime(), tonight, now).size();
        return new StudentDeadlinesResponse(window.size(), dueToday, window.stream().limit(size).toList());
    }

    private List<StudentDeadlineItemResponse> merged(Set<Long> sections, OffsetDateTime from, OffsetDateTime to, ZonedDateTime now) {
        return Stream.concat(
                assignments.findPublishedBetween(sections, from, to).stream().map(a -> item(a.getId(), "ASSIGNMENT",
                        a.getTitle(), a.getCourseSection().getCourse().getCourseCode(),
                        a.getCourseSection().getCourse().getCourseName(), a.getDueAt(), now)),
                exams.findScheduledBetween(sections, from, to).stream().map(x -> item(x.getId(), "EXAM",
                        x.getTitle(), x.getCourseSection().getCourse().getCourseCode(),
                        x.getCourseSection().getCourse().getCourseName(), x.getStartsAt(), now)))
                .sorted(Comparator.comparing(StudentDeadlineItemResponse::deadlineAt)
                        .thenComparing(StudentDeadlineItemResponse::type).thenComparing(StudentDeadlineItemResponse::id))
                .toList();
    }

    private StudentDeadlineItemResponse item(Long id, String type, String title, String code, String name, OffsetDateTime at, ZonedDateTime now) {
        return new StudentDeadlineItemResponse(id, type, title, code, name,
                at.atZoneSameInstant(properties.timezone()).toOffsetDateTime(),
                at.toInstant().isBefore(now.toInstant()) ? "PAST" : "UPCOMING");
    }
}
