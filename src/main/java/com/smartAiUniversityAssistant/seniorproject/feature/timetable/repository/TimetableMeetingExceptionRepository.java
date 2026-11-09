package com.smartAiUniversityAssistant.seniorproject.feature.timetable.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.timetable.entity.TimetableMeetingException;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface TimetableMeetingExceptionRepository extends JpaRepository<TimetableMeetingException, Long> {
    @EntityGraph(attributePaths = {"replacementRoom", "replacementRoom.building"})
    List<TimetableMeetingException> findByMeetingIdAndDeletedFalseOrderByOriginalDateAscIdAsc(long meetingId);
    Optional<TimetableMeetingException> findByIdAndMeetingIdAndDeletedFalse(long id, long meetingId);
    boolean existsByMeetingIdAndOriginalDateAndDeletedFalseAndIdNot(long meetingId, java.time.LocalDate originalDate, long id);

    /** Live exceptions of many meetings at once, with the replacement room and its building for display. */
    @Query("""
            select x from TimetableMeetingException x
            left join fetch x.replacementRoom rr left join fetch rr.building
            where x.meeting.id in :meetingIds and x.deleted = false
            order by x.originalDate asc, x.id asc
            """)
    List<TimetableMeetingException> findLiveForMeetings(@Param("meetingIds") Collection<Long> meetingIds);
}
