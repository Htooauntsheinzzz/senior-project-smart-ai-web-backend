package com.smartAiUniversityAssistant.seniorproject.feature.timetable.service;

import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.entity.CourseSectionMeeting;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.entity.TimetableMeetingException;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.enums.TimetableConflictType;
import java.time.LocalDate;
import java.util.*;

/** Room, lecturer and course-section conflicts between real occurrences of published meetings (spec file 42, Part M). */
public interface TimetableConflictService {
    /** Conflicts a candidate would have with published meetings, one entry per conflicting meeting and type. */
    List<TimetableConflict> check(ConflictCandidate candidate);

    /**
     * Conflicting pairs among the given published meetings, inside the optional date window. exceptions holds each
     * meeting's live exceptions; type narrows the result when given.
     */
    List<TimetableConflictPair> conflictsAmong(List<CourseSectionMeeting> published, Map<Long, List<TimetableMeetingException>> exceptions,
            LocalDate windowFrom, LocalDate windowTo, TimetableConflictType type);

    /**
     * Transaction-scoped PostgreSQL advisory locks on the section, lecturer and rooms, always taken in the same order,
     * so two admins cannot both see a free slot and publish into it. Must run inside the publishing transaction,
     * before the conflict query.
     */
    void lockResources(Long courseSectionId, Long lectureId, Collection<Long> roomIds);
}
