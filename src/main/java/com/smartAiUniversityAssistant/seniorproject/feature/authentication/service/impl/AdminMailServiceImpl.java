package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.impl;

import com.smartAiUniversityAssistant.seniorproject.config.AdminRegistrationProperties;
import com.smartAiUniversityAssistant.seniorproject.config.AppMailProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.AdminMailService;
import jakarta.mail.internet.InternetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Spec files 61 and 62: plain-text registration mails over SMTP. Nothing a mail contains (address, name, link) is logged.
 */
@Service
public class AdminMailServiceImpl implements AdminMailService {
    private static final Logger LOG = LoggerFactory.getLogger(AdminMailServiceImpl.class);
    private static final String PRODUCT = "Smart AI University Assistant";
    private final ObjectProvider<JavaMailSender> senders;
    private final AppMailProperties properties;
    private final AdminRegistrationProperties registration;

    public AdminMailServiceImpl(ObjectProvider<JavaMailSender> senders, AppMailProperties properties,
                                AdminRegistrationProperties registration) {
        this.senders = senders;
        this.properties = properties;
        this.registration = registration;
    }

    @Override
    public boolean sendVerification(String to, String firstName, String verificationLink, Duration validFor) {
        return send("verification", to, "Confirm your email address", """
                Hello %s,

                Thank you for registering for the %s admin panel.

                Please confirm your email address by opening this link:

                %s

                The link works for %s and can be used once. You can sign in after your email is confirmed.

                If you did not register, you can ignore this email. The account stays closed.
                """.formatted(name(firstName), PRODUCT, verificationLink, duration(validFor)));
    }

    /** The wording is Hsu's, 2026-11-17 (D89). */
    @Override
    public boolean sendAwaitingApproval(String to, String firstName) {
        return send("awaiting-approval", to, "Your registration is waiting for approval", """
                Hello %s,

                Your registration has been successfully completed and is currently awaiting admin approval. You will be notified once the approval process is complete.

                Thank you for your patience.

                You can sign in here:
                %s
                """.formatted(name(firstName), registration.loginUrl()));
    }

    /** The wording is Hsu's, 2026-11-17 (D89): no greeting and no link, so the name is not used. */
    @Override
    public boolean sendApproved(String to, String firstName) {
        return send("approved", to, "Your account is approved", """
                We are pleased to inform you that your submission has been successfully reviewed and approved by the administrator.
                Thank you for your patience and cooperation.
                Best regards,
                Admin Team
                """);
    }

    @Override
    public boolean sendRejected(String to, String firstName) {
        return send("not-approved", to, "Your registration was not approved", """
                Hello %s,

                Your registration for the %s admin panel was not approved, so this email address cannot sign in.

                The admin panel is for university staff. If you think this is a mistake, please contact IT Support.
                """.formatted(name(firstName), PRODUCT));
    }

    @Override
    public boolean sendAlreadyRegistered(String to, String firstName) {
        return send("already-registered", to, "You already have an account", """
                Hello %s,

                Somebody tried to register for the %s admin panel with this email address, but it already has an account.

                If that was you, sign in here:

                %s

                If it was not you, you can ignore this email. Nothing was changed.
                """.formatted(name(firstName), PRODUCT, registration.loginUrl()));
    }

    private boolean send(String kind, String to, String subject, String text) {
        if (!properties.enabled()) {
            LOG.info("ADMIN_MAIL_SKIPPED kind={} reason=disabled", kind);
            return false;
        }
        JavaMailSender sender = senders.getIfAvailable();
        if (sender == null) {
            LOG.warn("ADMIN_MAIL_SKIPPED kind={} reason=no_mail_server", kind);
            return false;
        }
        try {
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(properties.from(), properties.fromName(), StandardCharsets.UTF_8.name()));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, false);
            sender.send(message);
            LOG.info("ADMIN_MAIL_SENT kind={}", kind);
            return true;
        } catch (Exception e) {
            // MailException, MessagingException or a bad address: registration itself has already been saved.
            LOG.warn("ADMIN_MAIL_SEND_FAILED kind={} error={}", kind, e.getClass().getSimpleName());
            return false;
        }
    }

    /** The name as one line of text; a missing name becomes a neutral greeting. */
    private static String name(String firstName) {
        String cleaned = firstName == null ? "" : firstName.replaceAll("\\p{Cntrl}", " ").strip();
        return cleaned.isEmpty() || cleaned.equals("-") ? "there" : cleaned;
    }

    private static String duration(Duration value) {
        long hours = value.toHours();
        if (hours >= 48 && hours % 24 == 0) return (hours / 24) + " days";
        if (hours >= 1) return hours == 1 ? "1 hour" : hours + " hours";
        long minutes = Math.max(1, value.toMinutes());
        return minutes == 1 ? "1 minute" : minutes + " minutes";
    }
}
