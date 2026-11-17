package com.smartAiUniversityAssistant.seniorproject.feature.authentication.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Spec file 60 (V58): the link between an Admin web user and an account at an external sign-in provider.
 * providerSubject is the provider's stable id of the person (the "sub" claim); the email is read from the user.
 */
@Entity @Table(name = "app_user_external_identities") @Getter @Setter
public class AppUserExternalIdentity {
    public static final String GOOGLE = "GOOGLE";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id") private Long id;
    @Column(name = "user_id", nullable = false, updatable = false) private Long userId;
    @Column(name = "provider", nullable = false, updatable = false, length = 30) private String provider;
    @Column(name = "provider_subject", nullable = false, updatable = false, length = 255) private String providerSubject;
    @Column(name = "linked_at", nullable = false, updatable = false) private Instant linkedAt;
}
