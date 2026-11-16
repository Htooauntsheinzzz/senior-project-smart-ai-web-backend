package com.smartAiUniversityAssistant.seniorproject.feature.campus.entity;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.*;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "campus_rooms") @Getter @Setter
public class CampusRoom {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id", nullable = false) private CampusBuilding building;
    @Column(name = "room_number", nullable = false, length = 50) private String roomNumber;
    @Column(name = "room_name", length = 200) private String roomName;
    @Column(name = "floor_number", nullable = false) private short floorNumber;
    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false, length = 40) private CampusRoomType roomType;
    @Column(name = "capacity") private Integer capacity;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30) private CampusRoomStatus status;
    @Column(name = "is_deleted", nullable = false) private boolean deleted;
    @ManyToMany
    @JoinTable(name = "campus_room_facilities", joinColumns = @JoinColumn(name = "room_id"),
            inverseJoinColumns = @JoinColumn(name = "facility_id"))
    private Set<CampusFacility> facilities = new HashSet<>();
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
}
