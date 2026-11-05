package com.smartAiUniversityAssistant.seniorproject.feature.coursesection.entity;

import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.enums.CourseMeetingType;
import jakarta.persistence.*;
import java.time.*;
import lombok.Getter;
import lombok.Setter;

// dayOfWeek: 1 = Monday ... 7 = Sunday, the same numbering as java.time.DayOfWeek.
@Entity @Table(name = "course_section_meetings") @Getter @Setter
public class CourseSectionMeeting {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_section_id", nullable = false) private CourseSection courseSection;
    @Enumerated(EnumType.STRING)
    @Column(name = "meeting_type", nullable = false, length = 30) private CourseMeetingType meetingType;
    @Column(name = "day_of_week", nullable = false) private Short dayOfWeek;
    @Column(name = "start_time", nullable = false) private LocalTime startTime;
    @Column(name = "end_time", nullable = false) private LocalTime endTime;
    @Column(name = "effective_from", nullable = false) private LocalDate effectiveFrom;
    @Column(name = "effective_to", nullable = false) private LocalDate effectiveTo;
    @Column(name = "room_id") private Long roomId;
    @Column(name = "room_label", length = 150) private String roomLabel;
    @Column(name = "is_active", nullable = false) private boolean active;
    @Column(name = "is_deleted", nullable = false) private boolean deleted;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
}
