package com.smartAiUniversityAssistant.seniorproject.security;
import java.util.Optional;
/** Implemented by the student feature: confirms the refresh session is live and the account may act. */
public interface StudentAccessVerifier { Optional<AuthenticatedStudent> verify(long studentId, String sessionId); }
