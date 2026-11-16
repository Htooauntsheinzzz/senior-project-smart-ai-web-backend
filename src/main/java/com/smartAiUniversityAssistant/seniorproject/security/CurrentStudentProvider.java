package com.smartAiUniversityAssistant.seniorproject.security;

import com.smartAiUniversityAssistant.seniorproject.feature.authentication.exception.AuthenticationFailure;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the Student from the validated JWT in the SecurityContext, for callers such as AI tools that must never
 * choose a student id themselves. Mirrors the /api/v1/student/** route rule for code reached outside that route.
 */
@Component
public class CurrentStudentProvider {
    private static final String ROLE_STUDENT = "ROLE_STUDENT";

    public long getCurrentStudentId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) throw AuthenticationFailure.unauthorized();
        // An admin principal never becomes a Student context.
        if (!(authentication.getPrincipal() instanceof AuthenticatedStudent student)
                || authentication.getAuthorities().stream().noneMatch(a -> ROLE_STUDENT.equals(a.getAuthority())))
            throw new AccessDeniedException("Access is denied.");
        if (student.forcePasswordChange()) throw new PasswordChangeRequiredException();
        return student.studentId();
    }
}
