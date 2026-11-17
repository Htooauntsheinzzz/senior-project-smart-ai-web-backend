package com.smartAiUniversityAssistant.seniorproject.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Spec file 61: Admin self-registration (registration form and Google sign-in).
 *
 * @param allowedEmailDomain the only email domain that may register or sign in with Google, without the "@"
 * @param defaultRole        the role a self-registered user receives; SUPER_ADMIN is never allowed here
 * @param verificationUrl    the admin web page that receives the verification token as the query parameter "token"
 * @param loginUrl           the admin web login page named in the approved mail (spec file 62)
 * @param mailPerAddress     registration mails one address may trigger per minute (register and resend together)
 */
@Validated
@ConfigurationProperties("app.admin-registration")
public record AdminRegistrationProperties(boolean enabled,
        @NotBlank @Pattern(regexp = AdminRegistrationProperties.DOMAIN) String allowedEmailDomain,
        @NotBlank @Pattern(regexp = "ADMIN|ACADEMIC_ADMIN") String defaultRole,
        @NotNull Duration verificationTtl,
        @NotBlank @Pattern(regexp = "https?://[^\\s?#]+") String verificationUrl,
        @NotBlank @Pattern(regexp = "https?://\\S+") String loginUrl,
        @Min(1) @Max(60) int mailPerAddress,
        @Valid @NotNull Google google) {
    static final String DOMAIN = "[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+";

    /**
     * @param clientId     the OAuth 2.0 Web client ID of the admin web; empty switches Google sign-in off
     * @param jwkSetUri    where Google publishes the keys that sign its ID tokens
     * @param hostedDomain the Google Workspace domain (the "hd" claim) a Google account must belong to; empty means
     *                     the allowed email domain
     */
    public record Google(@NotNull @Size(max = 255) String clientId,
                         @NotBlank @Pattern(regexp = "https?://\\S+") String jwkSetUri,
                         @NotNull @Pattern(regexp = "|" + AdminRegistrationProperties.DOMAIN) String hostedDomain) {
        public boolean configured() { return !clientId.isBlank(); }
    }

    /** The Google Workspace domain a Google account must belong to: the configured one, otherwise the email domain. */
    public String googleHostedDomain() {
        return google.hostedDomain().isBlank() ? allowedEmailDomain : google.hostedDomain();
    }

    @AssertTrue public boolean isVerificationTtlSafe() {
        return verificationTtl != null && verificationTtl.compareTo(Duration.ofMinutes(5)) >= 0
                && verificationTtl.compareTo(Duration.ofDays(7)) <= 0;
    }
}
