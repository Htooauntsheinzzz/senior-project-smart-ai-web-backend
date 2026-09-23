package com.smartAiUniversityAssistant.seniorproject.feature.authentication.controller;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.AuthenticationService;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/admin/auth")
public class AuthenticationController {
    private final AuthenticationService authentication;
    public AuthenticationController(AuthenticationService authentication) { this.authentication=authentication; }
    @PostMapping("/login") public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return token(authentication.login(request));
    }
    @PostMapping("/refresh") public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return token(authentication.refresh(request));
    }
    @GetMapping("/me") public ResponseEntity<CurrentUserResponse> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(authentication.me(principal));
    }
    @PostMapping("/logout") public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedUser principal) {
        authentication.logout(principal); return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @PostMapping("/change-password") public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        authentication.changePassword(principal,request); return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    private ResponseEntity<TokenResponse> token(TokenResponse response) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma","no-cache").body(response);
    }
}
