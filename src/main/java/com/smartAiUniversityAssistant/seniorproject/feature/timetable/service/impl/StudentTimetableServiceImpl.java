package com.smartAiUniversityAssistant.seniorproject.feature.timetable.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.entity.CourseSectionMeeting;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.repository.CourseSectionMeetingRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto.TimetableOccurrence;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.entity.*;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.repository.TimetableMeetingExceptionRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.service.StudentTimetableService;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentTimetableServiceImpl implements StudentTimetableService {
    /** How far ahead the next class is searched; effective dates and term dates usually end much sooner. */
    static final int HORIZON_DAYS = 400;

    private final CourseSectionMeetingRepository meetings;
    private final TimetableMeetingExceptionRepository exceptions;

    public StudentTimetableServiceImpl(CourseSectionMeetingRepository meetings, TimetableMeetingExceptionRepository exceptions) {
        this.meetings = meetings;
        this.exceptions = exceptions;
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public List<CourseSectionMeeting> publishedMeetings(Collection<Long> sectionIds) {
        return sectionIds.isEmpty() ? List.of() : meetings.findPublishedForSections(sectionIds);
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public Map<Long, List<TimetableMeetingException>> upcomingExceptions(Collection<Long> meetingIds, LocalDate from) {
        if (meetingIds.isEmpty()) return Map.of();
        var result = new HashMap<Long, List<TimetableMeetingException>>();
        for (var x : exceptions.findLiveForMeetings(meetingIds)) {
            boolean upcoming = !x.getOriginalDate().isBefore(from) || x.getReplacementDate() != null && !x.getReplacementDate().isBefore(from);
            if (upcoming) result.computeIfAbsent(x.getMeeting().getId(), k -> new ArrayList<>()).add(x);
        }
        return result;
    }

    // Occurrences are generated per meeting from today inside its effective dates and, when the section has a term,
    // inside the term's dates; the earliest start strictly after now wins. Nothing is guessed when none exists.
    @Override @Transactional(readOnly = true, timeout = 15)
    public Optional<NextOccurrence> nextOccurrence(List<CourseSectionMeeting> published, ZonedDateTime now) {
        if (published.isEmpty()) return Optional.empty();
        var live = new HashMap<Long, List<TimetableMeetingException>>();
        for (var x : exceptions.findLiveForMeetings(published.stream().map(CourseSectionMeeting::getId).toList()))
            live.computeIfAbsent(x.getMeeting().getId(), k -> new ArrayList<>()).add(x);
        LocalDate today = now.toLocalDate();
        NextOccurrence best = null;
        for (var meeting : published) {
            var term = meeting.getCourseSection().getAcademicTerm();
            LocalDate from = today, to = today.plusDays(HORIZON_DAYS);
            if (term != null) {
                if (term.getStartDate().isAfter(from)) from = term.getStartDate();
                if (term.getEndDate().isBefore(to)) to = term.getEndDate();
            }
            if (from.isAfter(to)) continue;
            for (var o : TimetableOccurrences.expand(TimetableOccurrences.Rule.of(meeting), live.getOrDefault(meeting.getId(), List.of()), from, to, false)) {
                ZonedDateTime start = o.date().atTime(o.startTime()).atZone(now.getZone());
                if (!start.isAfter(now)) continue;
                if (best == null || start.isBefore(best.startsAt()))
                    best = new NextOccurrence(meeting, o, start, o.date().atTime(o.endTime()).atZone(now.getZone()));
                break; // occurrences are sorted, so the first future one is this meeting's earliest
            }
        }
        return Optional.ofNullable(best);
    }
}
