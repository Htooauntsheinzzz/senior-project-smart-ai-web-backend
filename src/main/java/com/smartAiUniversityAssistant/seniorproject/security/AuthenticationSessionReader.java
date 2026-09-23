package com.smartAiUniversityAssistant.seniorproject.security;
import java.util.Optional;
public interface AuthenticationSessionReader { Optional<SessionSecuritySnapshot> read(long userId, String sessionId); }
