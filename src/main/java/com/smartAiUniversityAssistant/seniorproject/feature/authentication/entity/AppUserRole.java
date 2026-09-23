package com.smartAiUniversityAssistant.seniorproject.feature.authentication.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="app_user_roles", uniqueConstraints=@UniqueConstraint(name="uk_app_user_roles_user_role", columnNames={"user_id","role_id"}))
@Getter @Setter
public class AppUserRole {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id") private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="user_id", nullable=false) private AppUser user;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="role_id", nullable=false) private AppRole role;
    @Column(name="assigned_at", nullable=false) private LocalDateTime assignedAt;
    @Column(name="assigned_by") private Long assignedBy;
}
