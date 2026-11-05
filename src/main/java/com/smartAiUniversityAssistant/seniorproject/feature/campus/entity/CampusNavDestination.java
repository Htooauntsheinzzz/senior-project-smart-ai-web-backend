package com.smartAiUniversityAssistant.seniorproject.feature.campus.entity;

import jakarta.persistence.*;
import java.time.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "campus_nav_destinations") @Getter @Setter
public class CampusNavDestination {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "node_id", nullable = false) private CampusNavNode node;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id") private CampusBuilding building;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id") private CampusPlace place;
    @Column(name = "is_primary", nullable = false) private boolean primary;
    @Column(name = "is_accessible_entrance", nullable = false) private boolean accessibleEntrance;
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
}
