package com.smartAiUniversityAssistant.seniorproject.feature.timetable.entity;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.entity.CampusRoom;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.entity.CourseSectionMeeting;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.enums.TimetableExceptionType;
import jakarta.persistence.*;
import java.time.*;
import lombok.Getter;
import lombok.Setter;

/** One occurrence of a recurring meeting that is cancelled or moved. A null replacement room means the meeting's room. */
@Entity @Table(name = "timetable_meeting_exceptions") @Getter @Setter
public class TimetableMeetingException {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false) private CourseSectionMeeting meeting;
    @Column(name = "original_date", nullable = false) private LocalDate originalDate;
    @Enumerated(EnumType.STRING)
    @Column(name = "exception_type", nullable = false, length = 20) private TimetableExceptionType exceptionType;
    @Column(name = "replacement_date") private LocalDate replacementDate;
    @Column(name = "replacement_start_time") private LocalTime replacementStartTime;
    @Column(name = "replacement_end_time") private LocalTime replacementEndTime;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replacement_room_id") private CampusRoom replacementRoom;
    @Column(name = "reason", length = 500) private String reason;
    @Column(name = "is_deleted", nullable = false) private boolean deleted;
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
}
