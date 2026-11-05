package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service.impl;

import com.smartAiUniversityAssistant.seniorproject.config.StudentAcademicProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.entity.*;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.enums.CourseSectionStatus;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.repository.CourseSectionMeetingRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.enums.EnrollmentStatus;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.repository.EnrollmentRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto.StudentNextClassResponse;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcademicScheduleServiceImpl implements AcademicScheduleService {
    static final String REGULAR_TIMETABLE = "REGULAR_TIMETABLE";

    private final StudentAcademicService academics;
    private final EnrollmentRepository enrollments;
    private final CourseSectionMeetingRepository meetings;
    private final StudentAcademicProperties properties;
    private final Clock clock;

    public AcademicScheduleServiceImpl(StudentAcademicService academics, EnrollmentRepository enrollments,
            CourseSectionMeetingRepository meetings, StudentAcademicProperties properties, Clock clock) {
        this.academics = academics;
        this.enrollments = enrollments;
        this.meetings = meetings;
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
        CourseSectionMeeting nextMeeting = null;
        LocalDateTime nextStart = null;
        for (CourseSectionMeeting meeting : meetings.findActiveForSections(sections.keySet())) {
            var start = NextClassCalculator.nextStart(meeting.getDayOfWeek(), meeting.getStartTime(),
                    meeting.getEffectiveFrom(), meeting.getEffectiveTo(), now.toLocalDateTime());
            if (start.isPresent() && (nextStart == null || start.get().isBefore(nextStart))) {
                nextStart = start.get();
                nextMeeting = meeting;
            }
        }
        if (nextMeeting == null) return Optional.empty();
        CourseSection section = sections.get(nextMeeting.getCourseSection().getId());
        var course = section.getCourse();
        ZonedDateTime startsAt = nextStart.atZone(properties.timezone());
        ZonedDateTime endsAt = nextStart.toLocalDate().atTime(nextMeeting.getEndTime()).atZone(properties.timezone());
        return Optional.of(new StudentNextClassResponse(course.getId(), course.getCourseCode(), course.getCourseName(),
                section.getId(), section.getSectionNumber(), nextMeeting.getMeetingType().name(),
                startsAt.getDayOfWeek().name(), startsAt.toOffsetDateTime(), endsAt.toOffsetDateTime(), null,
                nextMeeting.getRoomLabel(), ChronoUnit.MINUTES.between(now, startsAt), "UPCOMING", REGULAR_TIMETABLE));
    }
}
