package com.smartAiUniversityAssistant.seniorproject.config;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Spec file 61: outgoing application mail. The SMTP server itself is configured with Spring Boot's spring.mail.*.
 *
 * @param enabled  false: no mail is sent (the attempt is logged without the address or the content)
 * @param from     the sender address
 * @param fromName the sender name shown by mail clients
 */
@Validated
@ConfigurationProperties("app.mail")
public record AppMailProperties(boolean enabled, @NotBlank @Email @Size(max = 255) String from,
                                @NotBlank @Size(max = 100) String fromName) {}
