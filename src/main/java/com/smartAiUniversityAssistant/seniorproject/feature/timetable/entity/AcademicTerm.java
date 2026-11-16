package com.smartAiUniversityAssistant.seniorproject.feature.timetable.entity;

import com.smartAiUniversityAssistant.seniorproject.feature.semester.entity.Semester;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.enums.AcademicTermStatus;
import jakarta.persistence.*;
import java.time.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "academic_terms") @Getter @Setter
public class AcademicTerm {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "semester_id", nullable = false) private Semester semester;
    @Column(name = "academic_year_code", nullable = false, length = 20) private String academicYearCode;
    @Column(name = "academic_year_label", nullable = false, length = 30) private String academicYearLabel;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date", nullable = false) private LocalDate endDate;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20) private AcademicTermStatus status;
    @Column(name = "is_deleted", nullable = false) private boolean deleted;
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
}
