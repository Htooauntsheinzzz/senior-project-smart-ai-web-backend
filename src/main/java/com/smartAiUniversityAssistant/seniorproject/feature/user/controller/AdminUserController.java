package com.smartAiUniversityAssistant.seniorproject.feature.user.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.user.dto.AdminUserResponse;
import com.smartAiUniversityAssistant.seniorproject.feature.user.dto.CreateAdminUserRequest;
import com.smartAiUniversityAssistant.seniorproject.feature.user.dto.request.*;
import com.smartAiUniversityAssistant.seniorproject.feature.user.dto.response.*;
import com.smartAiUniversityAssistant.seniorproject.feature.user.service.AdminUserService;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    private final AdminUserService users;

    public AdminUserController(AdminUserService users) { this.users = users; }

    @PostMapping
    public ResponseEntity<AdminUserResponse> create(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody CreateAdminUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(users.create(actor, request));
    }

    @GetMapping
    public ResponseEntity<AdminUserPageResponse> list(@AuthenticationPrincipal AuthenticatedUser actor,
            HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(users.list(actor, AdminUserListQuery.from(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminUserDetailResponse> detail(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(users.detail(actor, id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminUserDetailResponse> update(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long id, @Valid @RequestBody UpdateAdminUserRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(users.update(actor, id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<AdminUserDetailResponse> updateStatus(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long id, @Valid @RequestBody UpdateAdminUserStatusRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(users.updateStatus(actor, id, request));
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<AdminUserDetailResponse> updateRole(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long id, @Valid @RequestBody UpdateAdminUserRoleRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(users.updateRole(actor, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id) {
        users.delete(actor, id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
