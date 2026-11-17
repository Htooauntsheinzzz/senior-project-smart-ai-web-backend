package com.smartAiUniversityAssistant.seniorproject.feature.authentication.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Spec file 60 (V59): the pending email verification of a form registration, at most one per user. The row is
 * replaced when a new link is sent and deleted when the email is verified. Only the token's SHA-256 hash is stored.
 */
@Entity @Table(name = "app_user_email_verifications") @Getter @Setter
public class AppUserEmailVerification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id") private Long id;
    @Column(name = "user_id", nullable = false, updatable = false) private Long userId;
    @Column(name = "token_hash", nullable = false, length = 64) private String tokenHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}
