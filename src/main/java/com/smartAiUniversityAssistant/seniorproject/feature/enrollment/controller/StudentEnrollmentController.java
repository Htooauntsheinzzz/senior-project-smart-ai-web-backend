package com.smartAiUniversityAssistant.seniorproject.feature.enrollment.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.service.StudentEnrollmentService;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto.StudentAcademicEnrollmentResponse;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedStudent;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Student self-enrollment from the mobile application (spec file 43). */
@RestController
@RequestMapping("/api/v1/student/enrollments")
public class StudentEnrollmentController {
    private final StudentEnrollmentService enrollments;
    public StudentEnrollmentController(StudentEnrollmentService enrollments) { this.enrollments = enrollments; }

    @GetMapping("/available-sections")
    public ResponseEntity<List<StudentAvailableSectionResponse>> availableSections(
            @AuthenticationPrincipal AuthenticatedStudent student) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(enrollments.availableSections(student.studentId()));
    }

    @PostMapping
    public ResponseEntity<StudentAcademicEnrollmentResponse> enroll(@AuthenticationPrincipal AuthenticatedStudent student,
            @Valid @RequestBody StudentEnrollRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(enrollments.enroll(student.studentId(), request));
    }

    // Confirms registration: the student's PENDING requests become ACTIVE without an admin (spec file 43, section 2.4).
    @PostMapping("/lock")
    public ResponseEntity<List<StudentAcademicEnrollmentResponse>> lock(@AuthenticationPrincipal AuthenticatedStudent student) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(enrollments.lock(student.studentId()));
    }

    // Locks one subject (spec file 43, section 2.5).
    @PostMapping("/{id:\\d+}/lock")
    public ResponseEntity<StudentAcademicEnrollmentResponse> lockOne(@AuthenticationPrincipal AuthenticatedStudent student,
            @PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(enrollments.lockOne(student.studentId(), id));
    }

    @PostMapping("/{id:\\d+}/withdraw")
    public ResponseEntity<StudentAcademicEnrollmentResponse> withdraw(@AuthenticationPrincipal AuthenticatedStudent student,
            @PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(enrollments.withdraw(student.studentId(), id));
    }
}
