package com.smartAiUniversityAssistant.seniorproject.feature.coursesection.entity;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.entity.CampusRoom;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.enums.CourseMeetingType;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.enums.TimetableMeetingStatus;
import jakarta.persistence.*;
import java.time.*;
import lombok.Getter;
import lombok.Setter;

/**
 * One weekly recurring class meeting of a course section: the timetable's source of truth (spec file 42).
 * dayOfWeek: 1 = Monday ... 7 = Sunday, the same numbering as java.time.DayOfWeek. Course, lecturer, faculty and
 * term are derived through the section; building and campus through the room. roomLabel is legacy text from before
 * campus rooms existed; active is the legacy flag and always equals status == PUBLISHED.
 */
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
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id") private CampusRoom room;
    @Column(name = "room_label", length = 150) private String roomLabel;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20) private TimetableMeetingStatus status;
    @Column(name = "notes", columnDefinition = "text") private String notes;
    @Column(name = "is_active", nullable = false) private boolean active;
    @Column(name = "is_deleted", nullable = false) private boolean deleted;
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;

    /** Keeps the legacy flag equal to the authoritative status. */
    public void setStatus(TimetableMeetingStatus value) {
        this.status = value;
        this.active = value == TimetableMeetingStatus.PUBLISHED;
    }
    public Long getRoomId() { return room == null ? null : room.getId(); }
}
