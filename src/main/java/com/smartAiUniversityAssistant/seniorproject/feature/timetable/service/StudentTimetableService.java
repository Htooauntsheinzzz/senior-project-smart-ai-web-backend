package com.smartAiUniversityAssistant.seniorproject.feature.timetable.service;

import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.entity.CourseSectionMeeting;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto.TimetableOccurrence;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.entity.TimetableMeetingException;
import java.time.*;
import java.util.*;

/**
 * Read-only timetable data for the student's own sections (spec file 42, Parts O and P). Callers resolve the student
 * from the JWT and pass only section ids; this service never sees a student id.
 */
public interface StudentTimetableService {
    /** Published meetings of the sections with room, building and campus loaded, ordered by weekday and start time. */
    List<CourseSectionMeeting> publishedMeetings(Collection<Long> sectionIds);

    /** Live exceptions of the meetings whose original or replacement date is on or after the given date. */
    Map<Long, List<TimetableMeetingException>> upcomingExceptions(Collection<Long> meetingIds, LocalDate from);

    /** The earliest occurrence starting strictly after now, with cancellations and reschedules applied. */
    Optional<NextOccurrence> nextOccurrence(List<CourseSectionMeeting> published, ZonedDateTime now);

    record NextOccurrence(CourseSectionMeeting meeting, TimetableOccurrence occurrence, ZonedDateTime startsAt, ZonedDateTime endsAt) {}
}
