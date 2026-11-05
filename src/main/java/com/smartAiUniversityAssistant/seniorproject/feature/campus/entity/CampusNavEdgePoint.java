package com.smartAiUniversityAssistant.seniorproject.feature.campus.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "campus_nav_edge_points") @Getter @Setter
public class CampusNavEdgePoint {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "edge_id", nullable = false) private Long edgeId;
    @Column(name = "point_sequence", nullable = false) private int pointSequence;
    @Column(name = "latitude", nullable = false, precision = 10, scale = 7) private BigDecimal latitude;
    @Column(name = "longitude", nullable = false, precision = 10, scale = 7) private BigDecimal longitude;
}
