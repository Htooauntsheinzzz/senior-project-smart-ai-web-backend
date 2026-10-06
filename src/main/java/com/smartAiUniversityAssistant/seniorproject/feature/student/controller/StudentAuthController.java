package com.smartAiUniversityAssistant.seniorproject.feature.student.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.student.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.student.service.StudentAuthService;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedStudent;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/student/auth")
public class StudentAuthController {
    private final StudentAuthService auth;
    public StudentAuthController(StudentAuthService auth) { this.auth = auth; }

    @PostMapping("/register")
    public ResponseEntity<StudentRegisterResponse> register(@Valid @RequestBody StudentRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(auth.register(request));
    }


    @PostMapping("/login")
    public ResponseEntity<StudentLoginResponse> login(@Valid @RequestBody StudentLoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(auth.login(request));
    }


    @PostMapping("/refresh")
    public ResponseEntity<StudentRefreshResponse> refresh(@Valid @RequestBody StudentRefreshRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(auth.refresh(request));
    }
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedStudent student,
            @RequestParam(defaultValue = "false") boolean allDevices) {
        auth.logout(student, allDevices);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthenticatedStudent student,
            @Valid @RequestBody StudentChangePasswordRequest request) {
        auth.changePassword(student, request);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @GetMapping("/me")
    public ResponseEntity<StudentMeResponse> me(@AuthenticationPrincipal AuthenticatedStudent student) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(auth.me(student));
    }
}
