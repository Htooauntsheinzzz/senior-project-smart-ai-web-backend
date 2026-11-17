package com.smartAiUniversityAssistant.seniorproject.feature.authentication;

import com.smartAiUniversityAssistant.seniorproject.config.AdminRegistrationProperties;
import com.smartAiUniversityAssistant.seniorproject.config.AppMailProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.impl.AdminMailServiceImpl;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Spec files 61 and 62: the registration mails, with the SMTP connection replaced. */
class AdminMailServiceTests {
    private final JavaMailSender sender = mock(JavaMailSender.class);

    @Test
    void theVerificationMailCarriesTheLinkAndTheApprovalMailsSayWhatHappensNext() throws Exception {
        var mail = service(true, sender);
        String link = "http://localhost:3000/verify-email?token=AbC-123_xyz";

        assertThat(mail.sendVerification("somchai.j@rsu.ac.th", "Somchai", link, Duration.ofHours(24))).isTrue();
        MimeMessage verification = sent(1);
        assertThat(verification.getSubject()).isEqualTo("Confirm your email address");
        assertThat(verification.getRecipients(Message.RecipientType.TO)).extracting(Object::toString).containsExactly("somchai.j@rsu.ac.th");
        var from = (InternetAddress) verification.getFrom()[0];
        assertThat(from.getAddress()).isEqualTo("no-reply@rsu.ac.th");
        assertThat(from.getPersonal()).isEqualTo("Smart AI University Assistant");
        assertThat(verification.isMimeType("text/plain")).isTrue();
        assertThat((String) verification.getContent()).startsWith("Hello Somchai,").contains(link, "24 hours", "If you did not register");

        // Spec file 62: verified, then approved or not approved. The wording of these two is Hsu's (D89).
        assertThat(mail.sendAwaitingApproval("somchai.j@rsu.ac.th", "Somchai")).isTrue();
        MimeMessage waiting = sent(2);
        assertThat(waiting.getSubject()).isEqualTo("Your registration is waiting for approval");
        assertThat(text(waiting)).isEqualTo("""
                Hello Somchai,

                Your registration has been successfully completed and is currently awaiting admin approval. You will be notified once the approval process is complete.

                Thank you for your patience.

                You can sign in here:
                http://localhost:3000/login
                """);

        assertThat(mail.sendApproved("anan@rsu.ac.th", "Anan")).isTrue();
        MimeMessage approved = sent(3);
        assertThat(approved.getSubject()).isEqualTo("Your account is approved");
        assertThat(text(approved)).isEqualTo("""
                We are pleased to inform you that your submission has been successfully reviewed and approved by the administrator.
                Thank you for your patience and cooperation.
                Best regards,
                Admin Team
                """);

        assertThat(mail.sendAlreadyRegistered("somchai.j@rsu.ac.th", "Somchai\r\nBcc: other@example.test")).isTrue();
        MimeMessage already = sent(4);
        assertThat(already.getSubject()).isEqualTo("You already have an account");
        // A name is one line of text and never a mail header.
        assertThat(already.getHeader("Bcc")).isNull();
        assertThat((String) already.getContent()).contains("Nothing was changed").startsWith("Hello Somchai  Bcc: other@example.test,");

        // A missing name ("-") becomes a neutral greeting.
        assertThat(mail.sendRejected("student@rsu.ac.th", "-")).isTrue();
        MimeMessage rejected = sent(5);
        assertThat(rejected.getSubject()).isEqualTo("Your registration was not approved");
        assertThat((String) rejected.getContent()).startsWith("Hello there,").contains("was not approved", "IT Support")
                .doesNotContain("http://localhost:3000/login");
    }

    @Test
    void aMailThatCannotBeSentNeverFailsTheCaller() {
        doThrow(new MailSendException("connection refused")).when(sender).send(any(MimeMessage.class));
        when(sender.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
        assertThat(service(true, sender).sendApproved("somchai.j@rsu.ac.th", "Somchai")).isFalse();

        // Mail switched off, or no mail server configured at all.
        var silent = mock(JavaMailSender.class);
        assertThat(service(false, silent).sendVerification("a@rsu.ac.th", "A", "http://x/verify?token=t", Duration.ofMinutes(30))).isFalse();
        verifyNoInteractions(silent);
        assertThat(service(true, null).sendAlreadyRegistered("a@rsu.ac.th", "A")).isFalse();
    }

    /** The mail body with the line ends as written in the code (SMTP may turn them into CRLF). */
    private static String text(MimeMessage message) throws Exception {
        return ((String) message.getContent()).replace("\r\n", "\n");
    }

    private MimeMessage sent(int count) {
        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender, times(count)).send(captor.capture());
        return captor.getAllValues().get(count - 1);
    }

    @SuppressWarnings("unchecked")
    private AdminMailServiceImpl service(boolean enabled, JavaMailSender mailSender) {
        if (mailSender != null)
            when(mailSender.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mailSender);
        return new AdminMailServiceImpl(provider, new AppMailProperties(enabled, "no-reply@rsu.ac.th", "Smart AI University Assistant"),
                new AdminRegistrationProperties(true, "rsu.ac.th", "ADMIN", Duration.ofHours(24),
                        "http://localhost:3000/verify-email", "http://localhost:3000/login", 3,
                        new AdminRegistrationProperties.Google("", "https://www.googleapis.com/oauth2/v3/certs", "")));
    }
}
