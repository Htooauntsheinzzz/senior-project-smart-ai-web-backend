package com.smartAiUniversityAssistant.seniorproject.feature.authentication.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="appuser_credentials") @Getter @Setter
public class AppUserCredentials {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id") private Long id;
    @OneToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="user_id", nullable=false, unique=true) private AppUser user;
    @Column(name="password_hash", nullable=false, length=255) private String passwordHash;
    @Column(name="force_password_change", nullable=false) private boolean forcePasswordChange;
    @Column(name="password_changed_at") private LocalDateTime passwordChangedAt;
    @Column(name="failed_login_attempts", nullable=false) private int failedLoginAttempts;
    @Column(name="locked_until") private LocalDateTime lockedUntil;
    @Column(name="created_at", nullable=false) private LocalDateTime createdAt;
    @Column(name="updated_at") private LocalDateTime updatedAt;
}
