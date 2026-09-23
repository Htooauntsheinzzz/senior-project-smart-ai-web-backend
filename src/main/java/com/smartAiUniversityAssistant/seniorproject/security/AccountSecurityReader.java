package com.smartAiUniversityAssistant.seniorproject.security;
import java.util.Optional;
public interface AccountSecurityReader { Optional<AccountSecuritySnapshot> read(long userId); }
