package com.smartAiUniversityAssistant.seniorproject.feature.coursesection.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.entity.CourseSectionMeeting;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CourseSectionMeetingRepository extends JpaRepository<CourseSectionMeeting, Long> {
    List<CourseSectionMeeting> findByCourseSectionIdAndDeletedFalseOrderByDayOfWeekAscStartTimeAscIdAsc(Long sectionId);
    Optional<CourseSectionMeeting> findByIdAndCourseSectionIdAndDeletedFalse(Long id, Long sectionId);

    @Query("""
            select m from CourseSectionMeeting m
            where m.courseSection.id in :sectionIds and m.active = true and m.deleted = false
            order by m.dayOfWeek asc, m.startTime asc, m.id asc
            """)
    List<CourseSectionMeeting> findActiveForSections(@Param("sectionIds") Collection<Long> sectionIds);

}
