package com.smartAiUniversityAssistant.seniorproject.feature.campus.entity;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.CampusBuildingStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "campus_buildings") @Getter @Setter
public class CampusBuilding {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false) private CampusSite site;
    @Column(name = "building_code", nullable = false, length = 50) private String buildingCode;
    @Column(name = "building_name", nullable = false, length = 200) private String buildingName;
    @Column(name = "building_name_th", length = 200) private String buildingNameTh;
    @Column(name = "description", columnDefinition = "text") private String description;
    @Column(name = "total_floors", nullable = false) private short totalFloors;
    @Column(name = "area_square_meters", precision = 12, scale = 2) private BigDecimal areaSquareMeters;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30) private CampusBuildingStatus status;
    @Column(name = "is_deleted", nullable = false) private boolean deleted;
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
}
