package com.smartAiUniversityAssistant.seniorproject.feature.campus.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "campus_sites") @Getter @Setter
public class CampusSite {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "site_code", nullable = false, length = 50) private String siteCode;
    @Column(name = "site_name", nullable = false, length = 200) private String siteName;
    @Column(name = "description", columnDefinition = "text") private String description;
    @Column(name = "timezone", nullable = false, length = 60) private String timezone;
    @Column(name = "center_latitude", precision = 10, scale = 7) private BigDecimal centerLatitude;
    @Column(name = "center_longitude", precision = 10, scale = 7) private BigDecimal centerLongitude;
    @Column(name = "is_active", nullable = false) private boolean active;
    @Column(name = "is_deleted", nullable = false) private boolean deleted;
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
}
