package com.smartAiUniversityAssistant.seniorproject.feature.campus.entity;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.CampusNodeType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "campus_nav_nodes") @Getter @Setter
public class CampusNavNode {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false) private CampusSite site;
    @Column(name = "node_code", nullable = false, length = 50) private String nodeCode;
    @Column(name = "node_name", nullable = false, length = 200) private String nodeName;
    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", nullable = false, length = 30) private CampusNodeType nodeType;
    @Column(name = "latitude", nullable = false, precision = 18, scale = 15) private BigDecimal latitude;
    @Column(name = "longitude", nullable = false, precision = 18, scale = 15) private BigDecimal longitude;
    @Column(name = "is_accessible", nullable = false) private boolean accessible;
    @Column(name = "is_active", nullable = false) private boolean active;
    @Column(name = "is_deleted", nullable = false) private boolean deleted;
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
}
