package com.smartAiUniversityAssistant.seniorproject.feature.faculty.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "faculties") @Getter @Setter
public class Faculty {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id") private Long id;
    @Column(name = "faculty_code", nullable = false, length = 30, unique = true) private String facultyCode;
    @Column(name = "faculty_name_en", nullable = false, length = 255, unique = true) private String facultyNameEn;
    @Column(name = "faculty_name_th", length = 255) private String facultyNameTh;
    @Column(name = "is_active", nullable = false) private boolean active;
    @Column(name = "is_deleted", nullable = false) private boolean deleted;
    @Column(name = "created_by", nullable = false) private Long createdBy;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
}
