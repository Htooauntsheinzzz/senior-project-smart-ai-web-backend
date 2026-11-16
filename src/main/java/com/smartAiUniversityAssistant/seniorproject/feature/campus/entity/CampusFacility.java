package com.smartAiUniversityAssistant.seniorproject.feature.campus.entity;

import jakarta.persistence.*;
import java.time.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "campus_facilities") @Getter @Setter
public class CampusFacility {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "facility_code", nullable = false, length = 60) private String facilityCode;
    @Column(name = "facility_name", nullable = false, length = 150) private String facilityName;
    @Column(name = "description", columnDefinition = "text") private String description;
    @Column(name = "is_active", nullable = false) private boolean active;
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
}
