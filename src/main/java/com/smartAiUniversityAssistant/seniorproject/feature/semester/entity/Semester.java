package com.smartAiUniversityAssistant.seniorproject.feature.semester.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "semesters") @Getter @Setter
public class Semester {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "academic_year", nullable = false) private Integer academicYear;
    @Column(name = "semester_name_th", nullable = false, length = 150) private String semesterNameTh;
    @Column(name = "semester_name_en", nullable = false, length = 150) private String semesterNameEn;
    @Column(name = "is_deleted", nullable = false) private boolean deleted;
    @Column(name = "created_by", nullable = false) private Long createdBy;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
}
