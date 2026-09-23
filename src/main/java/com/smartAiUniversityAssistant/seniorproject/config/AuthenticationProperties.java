package com.smartAiUniversityAssistant.seniorproject.config;

import java.time.Duration;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.auth")
public record AuthenticationProperties(@Valid @NotNull Jwt jwt, @Valid @NotNull Session session,
        @Valid @NotNull Password password, @Valid @NotNull Lockout lockout,
        @Valid @NotNull Cors cors, @Valid @NotNull Throttle throttle) {
    public record Jwt(@NotBlank String issuer, @NotBlank String audience, @NotBlank String keyId,
                      @NotNull Duration accessTokenTtl, @NotNull Duration clockSkew) {
        @AssertTrue public boolean isSafe() {
            return positive(accessTokenTtl, Duration.ofMinutes(15)) && clockSkew != null
                    && !clockSkew.isNegative() && clockSkew.compareTo(Duration.ofSeconds(30)) <= 0;
        }
    }
    public record Session(@Pattern(regexp="susa:[a-zA-Z0-9_-]+:auth") @NotBlank String keyPrefix,
                          @NotNull Duration refreshTtl, @NotNull Duration passwordChangeTtl,
                          @Min(1) @Max(2000) int maxRotations) {
        @AssertTrue public boolean isSafe() {
            return positive(refreshTtl, Duration.ofDays(7)) && positive(passwordChangeTtl, Duration.ofMinutes(10));
        }
    }
    public record Password(@Min(4) @Max(16) int bcryptStrength, @Min(15) int minCodePoints,
                           @Min(1) @Max(72) int maxUtf8Bytes) {}
    public record Lockout(@Min(1) @Max(100) int maxFailedAttempts, @NotNull Duration duration) {
        @AssertTrue public boolean isSafe() { return positive(duration, Duration.ofDays(1)); }
    }
    public record Cors(@NotEmpty List<@NotBlank String> allowedOrigins) {
        @AssertTrue public boolean isSafe() {
            return allowedOrigins != null && allowedOrigins.stream().allMatch(origin -> {
                try {
                    var uri = java.net.URI.create(origin);
                    return List.of("http", "https").contains(uri.getScheme()) && uri.getHost() != null
                            && uri.getRawUserInfo() == null && uri.getRawQuery() == null
                            && uri.getRawFragment() == null && uri.getRawPath().isEmpty();
                } catch (RuntimeException e) { return false; }
            });
        }
    }
    public record Throttle(@Min(1) int loginPerIp, @Min(1) int loginPerIdentifier,
                           @Min(1) int refreshPerIp) {}
    private static boolean positive(Duration value, Duration maximum) {
        return value != null && value.compareTo(Duration.ofSeconds(1)) >= 0 && value.compareTo(maximum) <= 0;
    }
}
