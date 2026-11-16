package com.smartAiUniversityAssistant.seniorproject.config;

import jakarta.validation.constraints.*;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** University timezone and the bounds of the student deadline list (spec file 40). */
@Validated
@ConfigurationProperties("app.student-academic")
public record StudentAcademicProperties(@NotNull ZoneId timezone,
        @Min(1) int deadlineDefaultLimit, @Min(1) int deadlineMaxLimit,
        @Min(1) int deadlineDefaultWindowDays, @Min(1) int deadlineMaxWindowDays,
        @Min(1) int homeDeadlineLimit) {}
