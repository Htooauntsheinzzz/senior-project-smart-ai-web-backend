package com.smartAiUniversityAssistant.seniorproject.feature.authentication.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="app_roles") @Getter @Setter
public class AppRole {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id") private Long id;
    @Column(name="role_code", nullable=false, unique=true, length=50) private String roleCode;
    @Column(name="role_name", nullable=false, unique=true, length=100) private String roleName;
    @Column(name="description", length=500) private String description;
    @Column(name="is_active", nullable=false) private boolean active;
    @Column(name="created_at", nullable=false) private LocalDateTime createdAt;
    @Column(name="updated_at") private LocalDateTime updatedAt;
}
