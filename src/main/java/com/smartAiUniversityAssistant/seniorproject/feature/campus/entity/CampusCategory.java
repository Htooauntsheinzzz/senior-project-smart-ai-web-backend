package com.smartAiUniversityAssistant.seniorproject.feature.campus.entity;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.CampusMobileGroup;
import jakarta.persistence.*;
import java.time.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "campus_categories") @Getter @Setter
public class CampusCategory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "category_code", nullable = false, length = 60) private String categoryCode;
    @Column(name = "category_name", nullable = false, length = 150) private String categoryName;
    @Enumerated(EnumType.STRING)
    @Column(name = "mobile_group", nullable = false, length = 40) private CampusMobileGroup mobileGroup;
    @Column(name = "description", columnDefinition = "text") private String description;
    @Column(name = "is_active", nullable = false) private boolean active;
    @Column(name = "is_deleted", nullable = false) private boolean deleted;
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
}
