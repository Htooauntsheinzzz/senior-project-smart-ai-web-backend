package com.smartAiUniversityAssistant.seniorproject.feature.authentication.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "app_users") @Getter @Setter
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name="id") private Long id;
    @Column(name="employee_id", nullable=false, length=50, unique=true) private String employeeId;
    @Column(name="first_name", nullable=false, length=100) private String firstName;
    @Column(name="last_name", nullable=false, length=100) private String lastName;
    @Column(name="email", nullable=false, length=255, unique=true) private String email;
    @Column(name="phone_number", length=30) private String phoneNumber;
    @Column(name="department_id") private Long departmentId;
    @Column(name="account_status", nullable=false, length=30) private String accountStatus;
    @Column(name="is_deleted", nullable=false) private boolean deleted;
    @Column(name="created_by") private Long createdBy;
    @Column(name="created_at", nullable=false) private LocalDateTime createdAt;
    @Column(name="updated_by") private Long updatedBy;
    @Column(name="updated_at") private LocalDateTime updatedAt;
}
