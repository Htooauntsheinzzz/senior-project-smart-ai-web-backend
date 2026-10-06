package com.smartAiUniversityAssistant.seniorproject.feature.student.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.student.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.student.service.StudentProfileService;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedStudent;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/student/profile")
public class StudentProfileController {
    private final StudentProfileService profiles;
    public StudentProfileController(StudentProfileService profiles) { this.profiles = profiles; }

    @PutMapping
    public ResponseEntity<StudentMeResponse> update(@AuthenticationPrincipal AuthenticatedStudent student,
            @Valid @RequestBody StudentProfileUpdateRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(profiles.updateProfile(student.studentId(), request));
    }
}
