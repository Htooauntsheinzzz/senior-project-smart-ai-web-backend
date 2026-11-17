package com.smartAiUniversityAssistant.seniorproject.feature.authentication.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "app_users") @Getter @Setter
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name="id") private Long id;
    public static final String ACTIVE = "ACTIVE";
    /** Spec file 61: a form registration whose email is not verified yet. Such an account cannot sign in. */
    public static final String PENDING_VERIFICATION = "PENDING_VERIFICATION";
    /** Spec file 62: the email is proven, a Super Admin has not approved the registration yet. Cannot sign in. */
    public static final String PENDING_APPROVAL = "PENDING_APPROVAL";
    public static final String SOURCE_ADMIN_CREATED = "ADMIN_CREATED";
    public static final String SOURCE_SELF_FORM = "SELF_FORM";
    public static final String SOURCE_GOOGLE = "GOOGLE";

    /** Null for a self-registered user until a Super Admin enters it (V57). */
    @Column(name="employee_id", length=50, unique=true) private String employeeId;
    @Column(name="first_name", nullable=false, length=100) private String firstName;
    @Column(name="last_name", nullable=false, length=100) private String lastName;
    @Column(name="email", nullable=false, length=255, unique=true) private String email;
    @Column(name="phone_number", length=30) private String phoneNumber;
    @Column(name="department_id") private Long departmentId;
    @Column(name="account_status", nullable=false, length=30) private String accountStatus;
    /** ADMIN_CREATED, SELF_FORM or GOOGLE (V57). */
    @Column(name="registration_source", nullable=false, length=20) private String registrationSource = SOURCE_ADMIN_CREATED;
    /** When the owner proved the email address; null when it was never proven (V57). */
    @Column(name="email_verified_at") private LocalDateTime emailVerifiedAt;
    @Column(name="is_deleted", nullable=false) private boolean deleted;
    @Column(name="created_by") private Long createdBy;
    @Column(name="created_at", nullable=false) private LocalDateTime createdAt;
    @Column(name="updated_by") private Long updatedBy;
    @Column(name="updated_at") private LocalDateTime updatedAt;
}
