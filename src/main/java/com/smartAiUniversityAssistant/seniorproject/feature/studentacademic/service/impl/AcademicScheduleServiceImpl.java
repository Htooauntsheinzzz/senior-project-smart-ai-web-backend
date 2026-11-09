package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service.impl;

import com.smartAiUniversityAssistant.seniorproject.config.StudentAcademicProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.entity.CampusRoom;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.repository.CampusRoomRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.entity.*;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.enums.CourseSectionStatus;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.enums.EnrollmentStatus;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.repository.EnrollmentRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto.StudentNextClassResponse;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service.*;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto.TimetableOccurrence;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.service.StudentTimetableService;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Next class from the published timetable (spec file 42, Part P): ACTIVE enrollments in ACTIVE sections, published
 * meetings, academic-term dates, cancellations and reschedules applied, earliest future start in the university
 * timezone. No class means an empty result, never a guess.
 */
@Service
public class AcademicScheduleServiceImpl implements AcademicScheduleService {
    static final String PUBLISHED_TIMETABLE = "PUBLISHED_TIMETABLE";

    private final StudentAcademicService academics;
    private final EnrollmentRepository enrollments;
    private final StudentTimetableService timetable;
    private final CampusRoomRepository rooms;
    private final StudentAcademicProperties properties;
    private final Clock clock;

    public AcademicScheduleServiceImpl(StudentAcademicService academics, EnrollmentRepository enrollments,
            StudentTimetableService timetable, CampusRoomRepository rooms, StudentAcademicProperties properties, Clock clock) {
        this.academics = academics;
        this.enrollments = enrollments;
        this.timetable = timetable;
        this.rooms = rooms;
        this.properties = properties;
        this.clock = clock;
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public Optional<StudentNextClassResponse> getNextClass(long studentId) {
        academics.requireEligibleStudent(studentId);
        Map<Long, CourseSection> sections = enrollments.findStudentAcademicEnrollments(studentId, EnumSet.of(EnrollmentStatus.ACTIVE))
                .stream().map(e -> e.getCourseSection())
                .filter(s -> CourseSectionStatus.ACTIVE.getValue().equals(s.getStatus()))
                .collect(Collectors.toMap(CourseSection::getId, Function.identity(), (first, second) -> first));
        if (sections.isEmpty()) return Optional.empty();
        ZonedDateTime now = clock.instant().atZone(properties.timezone());
        var next = timetable.nextOccurrence(timetable.publishedMeetings(sections.keySet()), now);
        if (next.isEmpty()) return Optional.empty();
        CourseSectionMeeting meeting = next.get().meeting();
        TimetableOccurrence occurrence = next.get().occurrence();
        CourseSection section = sections.get(meeting.getCourseSection().getId());
        var course = section.getCourse();
        CampusRoom room = occurrence.roomId() == null ? null
                : occurrence.roomId().equals(meeting.getRoomId()) ? meeting.getRoom() : rooms.findById(occurrence.roomId()).orElse(null);
        var building = room == null ? null : room.getBuilding();
        return Optional.of(new StudentNextClassResponse(course.getId(), course.getCourseCode(), course.getCourseName(),
                section.getId(), section.getSectionNumber(), meeting.getMeetingType().name(),
                next.get().startsAt().getDayOfWeek().name(), next.get().startsAt().toOffsetDateTime(), next.get().endsAt().toOffsetDateTime(),
                building == null ? null : building.getBuildingName(), room == null ? meeting.getRoomLabel() : room.getRoomNumber(),
                ChronoUnit.MINUTES.between(now, next.get().startsAt()), "UPCOMING", PUBLISHED_TIMETABLE,
                meeting.getId(), room == null ? null : room.getId(), room == null ? null : room.getRoomNumber(),
                building == null ? null : building.getId(), building == null ? null : building.getSite().getId(),
                occurrence.kind(), occurrence.originalDate()));
    }
}
