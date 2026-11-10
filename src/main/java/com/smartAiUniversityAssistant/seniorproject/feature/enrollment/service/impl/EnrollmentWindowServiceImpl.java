package com.smartAiUniversityAssistant.seniorproject.feature.enrollment.service.impl;

import com.smartAiUniversityAssistant.seniorproject.config.StudentAcademicProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.repository.CourseSectionMeetingRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.dto.EnrollmentWindow;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.exception.EnrollmentFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.service.EnrollmentWindowService;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnrollmentWindowServiceImpl implements EnrollmentWindowService {
    private final CourseSectionMeetingRepository meetings;
    private final StudentAcademicProperties properties;
    private final Clock clock;

    public EnrollmentWindowServiceImpl(CourseSectionMeetingRepository meetings, StudentAcademicProperties properties, Clock clock) {
        this.meetings = meetings;
        this.properties = properties;
        this.clock = clock;
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public Map<Long, EnrollmentWindow> windows(Collection<Long> courseSectionIds) {
        var result = new HashMap<Long, EnrollmentWindow>();
        if (courseSectionIds.isEmpty()) return result;
        // The strictest (earliest) due date of the section's published meetings decides; the due date itself is still open.
        LocalDate today = LocalDate.ofInstant(clock.instant(), properties.timezone());
        var scheduled = new HashSet<Long>();
        var due = new HashMap<Long, LocalDate>();
        for (var meeting : meetings.findPublishedForSections(new HashSet<>(courseSectionIds))) {
            Long id = meeting.getCourseSection().getId();
            scheduled.add(id);
            if (meeting.getEnrollmentDueDate() != null) due.merge(id, meeting.getEnrollmentDueDate(), (a, b) -> a.isBefore(b) ? a : b);
        }
        for (Long id : courseSectionIds) {
            LocalDate date = due.get(id);
            result.put(id, scheduled.contains(id) ? new EnrollmentWindow(true, date, date != null && today.isAfter(date))
                    : EnrollmentWindow.UNSCHEDULED);
        }
        return result;
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public void requireOpen(long courseSectionId) {
        EnrollmentWindow window = windows(List.of(courseSectionId)).get(courseSectionId);
        if (!window.scheduled()) throw new EnrollmentFailure(409, "COURSE_SECTION_NOT_IN_TIMETABLE",
                "The selected course section has no published timetable, so it cannot be enrolled yet.");
        if (window.closed()) throw new EnrollmentFailure(409, "ENROLLMENT_DUE_DATE_PASSED",
                "Enrollment for this course section closed on " + window.dueDate() + ".");
    }
}
