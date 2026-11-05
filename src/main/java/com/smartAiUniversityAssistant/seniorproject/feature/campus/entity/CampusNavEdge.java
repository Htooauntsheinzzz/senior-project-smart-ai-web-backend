package com.smartAiUniversityAssistant.seniorproject.feature.campus.entity;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "campus_nav_edges") @Getter @Setter
public class CampusNavEdge {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false) private CampusSite site;
    @Column(name = "edge_code", nullable = false, length = 50) private String edgeCode;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "start_node_id", nullable = false) private CampusNavNode startNode;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "end_node_id", nullable = false) private CampusNavNode endNode;
    @Column(name = "distance_meters", nullable = false, precision = 10, scale = 2) private BigDecimal distanceMeters;
    @Enumerated(EnumType.STRING)
    @Column(name = "surface_type", nullable = false, length = 40) private CampusSurfaceType surfaceType;
    @Column(name = "is_wheelchair_accessible", nullable = false) private boolean wheelchairAccessible;
    @Column(name = "is_bidirectional", nullable = false) private boolean bidirectional;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30) private CampusEdgeStatus status;
    @Column(name = "is_deleted", nullable = false) private boolean deleted;
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
}
