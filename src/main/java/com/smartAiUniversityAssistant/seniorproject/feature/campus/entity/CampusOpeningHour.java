package com.smartAiUniversityAssistant.seniorproject.feature.campus.entity;

import jakarta.persistence.*;
import java.time.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "campus_opening_hours") @Getter @Setter
public class CampusOpeningHour {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "building_id") private Long buildingId;
    @Column(name = "place_id") private Long placeId;
    @Column(name = "day_of_week", nullable = false) private short dayOfWeek;
    @Column(name = "opens_at") private LocalTime opensAt;
    @Column(name = "closes_at") private LocalTime closesAt;
    @Column(name = "is_closed", nullable = false) private boolean closed;
}
