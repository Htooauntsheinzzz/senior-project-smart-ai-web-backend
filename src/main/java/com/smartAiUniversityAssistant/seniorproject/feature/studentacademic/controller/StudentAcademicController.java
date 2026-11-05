package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedStudent;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.node.NullNode;

// The Student is always the token's principal; no route or parameter accepts a student id.
@RestController
@RequestMapping("/api/v1/student/academic")
public class StudentAcademicController {
    private final StudentAcademicService academics;
    private final AcademicProgressService progress;
    private final AcademicScheduleService schedules;
    private final AcademicDeadlineService deadlines;
    public StudentAcademicController(StudentAcademicService academics, AcademicProgressService progress,
            AcademicScheduleService schedules, AcademicDeadlineService deadlines) {
        this.academics = academics;
        this.progress = progress;
        this.schedules = schedules;
        this.deadlines = deadlines;
    }

    @GetMapping("/profile")
    public ResponseEntity<StudentAcademicProfileResponse> profile(@AuthenticationPrincipal AuthenticatedStudent student) {
        return ok(academics.getProfile(student.studentId()));
    }

    @GetMapping("/enrollments")
    public ResponseEntity<List<StudentAcademicEnrollmentResponse>> enrollments(
            @AuthenticationPrincipal AuthenticatedStudent student,
            @RequestParam(defaultValue = "ACTIVE") String status) {
        return ok(academics.getEnrollments(student.studentId(), status));
    }

    @GetMapping("/courses")
    public ResponseEntity<List<StudentAcademicCourseResponse>> courses(
            @AuthenticationPrincipal AuthenticatedStudent student,
            @RequestParam(defaultValue = "ACTIVE") String status) {
        return ok(academics.getCourses(student.studentId(), status));
    }

    @GetMapping("/schedule")
    public ResponseEntity<List<StudentAcademicScheduleResponse>> schedule(
            @AuthenticationPrincipal AuthenticatedStudent student,
            @RequestParam(defaultValue = "ACTIVE") String status) {
        return ok(academics.getSchedule(student.studentId(), status));
    }

    @GetMapping("/lecturers")
    public ResponseEntity<List<StudentAcademicLecturerResponse>> lecturers(
            @AuthenticationPrincipal AuthenticatedStudent student,
            @RequestParam(defaultValue = "ACTIVE") String status) {
        return ok(academics.getLecturers(student.studentId(), status));
    }

    @GetMapping("/progress")
    public ResponseEntity<StudentAcademicProgressResponse> progress(@AuthenticationPrincipal AuthenticatedStudent student) {
        return ok(progress.getProgress(student.studentId()));
    }

    @GetMapping("/courses/summary")
    public ResponseEntity<StudentCurrentSemesterSummaryResponse> coursesSummary(@AuthenticationPrincipal AuthenticatedStudent student) {
        return ok(progress.getCurrentSemesterSummary(student.studentId()));
    }

    // No upcoming class is HTTP 200 with the JSON literal null: the API has no data wrapper to carry "data: null".
    @GetMapping("/next-class")
    public ResponseEntity<Object> nextClass(@AuthenticationPrincipal AuthenticatedStudent student) {
        Object body = schedules.getNextClass(student.studentId()).map(Object.class::cast).orElse(NullNode.getInstance());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    @GetMapping("/deadlines")
    public ResponseEntity<StudentDeadlinesResponse> deadlines(@AuthenticationPrincipal AuthenticatedStudent student,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return ok(deadlines.getDeadlines(student.studentId(), limit, from, to));
    }

    private static <T> ResponseEntity<T> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
