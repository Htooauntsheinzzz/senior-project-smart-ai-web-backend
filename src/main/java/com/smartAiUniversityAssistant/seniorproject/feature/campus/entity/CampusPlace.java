package com.smartAiUniversityAssistant.seniorproject.feature.campus.entity;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.CampusPlaceStatus;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "campus_places") @Getter @Setter
public class CampusPlace {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false) private CampusSite site;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false) private CampusCategory category;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id") private CampusBuilding building;
    @Column(name = "place_code", nullable = false, length = 50) private String placeCode;
    @Column(name = "place_name", nullable = false, length = 200) private String placeName;
    @Column(name = "place_name_th", length = 200) private String placeNameTh;
    @Column(name = "description", columnDefinition = "text") private String description;
    @Column(name = "floor_label", length = 60) private String floorLabel;
    @Column(name = "phone_number", length = 50) private String phoneNumber;
    @Column(name = "is_popular", nullable = false) private boolean popular;
    @Column(name = "is_visible_on_mobile", nullable = false) private boolean visibleOnMobile;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30) private CampusPlaceStatus status;
    @Column(name = "is_deleted", nullable = false) private boolean deleted;
    @ManyToMany
    @JoinTable(name = "campus_place_facilities", joinColumns = @JoinColumn(name = "place_id"),
            inverseJoinColumns = @JoinColumn(name = "facility_id"))
    private Set<CampusFacility> facilities = new HashSet<>();
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
}
