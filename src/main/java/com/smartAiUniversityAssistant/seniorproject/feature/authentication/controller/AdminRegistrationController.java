package com.smartAiUniversityAssistant.seniorproject.feature.authentication.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.authentication.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.AdminRegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Spec file 61: public endpoints of Admin self-registration (registration form and Google sign-in). */
@RestController @RequestMapping("/api/v1/admin/auth")
public class AdminRegistrationController {
    private final AdminRegistrationService registration;
    public AdminRegistrationController(AdminRegistrationService registration) { this.registration = registration; }

    @PostMapping("/register")
    public ResponseEntity<AdminRegistrationResponse> register(@Valid @RequestBody AdminRegisterRequest request) {
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(registration.register(request));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<VerifyEmailResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(registration.verifyEmail(request));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<AdminRegistrationResponse> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(registration.resendVerification(request));
    }

    /** The same response as a password login. */
    @PostMapping("/google")
    public ResponseEntity<TokenResponse> google(@Valid @RequestBody GoogleSignInRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma", "no-cache")
                .body(registration.signInWithGoogle(request));
    }
}
