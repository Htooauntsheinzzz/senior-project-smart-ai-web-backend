package com.smartAiUniversityAssistant.seniorproject.feature.department.entity;

import com.smartAiUniversityAssistant.seniorproject.feature.faculty.entity.Faculty;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "departments") @Getter @Setter
public class Department {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "department_code", nullable = false, length = 30) private String departmentCode;
    @Column(name = "department_name", nullable = false, length = 255) private String departmentName;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "faculty_id", nullable = false) private Faculty faculty;
    @Column(name = "is_active", nullable = false) private boolean active;
    @Column(name = "is_deleted", nullable = false) private boolean deleted;
    @Column(name = "created_by", nullable = false) private Long createdBy;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
}
