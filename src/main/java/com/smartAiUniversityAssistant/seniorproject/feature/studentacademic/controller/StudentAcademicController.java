package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service.StudentAcademicService;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedStudent;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// The Student is always the token's principal; no route or parameter accepts a student id.
@RestController
@RequestMapping("/api/v1/student/academic")
public class StudentAcademicController {
    private final StudentAcademicService academics;
    public StudentAcademicController(StudentAcademicService academics) { this.academics = academics; }

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

    private static <T> ResponseEntity<T> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
