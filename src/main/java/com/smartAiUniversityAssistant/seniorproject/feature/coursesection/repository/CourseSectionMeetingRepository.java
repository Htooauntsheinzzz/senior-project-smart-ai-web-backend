package com.smartAiUniversityAssistant.seniorproject.feature.coursesection.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.entity.CourseSectionMeeting;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.enums.TimetableMeetingStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

/** Shared by the course-section meeting facade, the timetable feature and the student schedule. */
public interface CourseSectionMeetingRepository extends JpaRepository<CourseSectionMeeting, Long>, JpaSpecificationExecutor<CourseSectionMeeting> {
    List<CourseSectionMeeting> findByCourseSectionIdAndDeletedFalseOrderByDayOfWeekAscStartTimeAscIdAsc(Long sectionId);
    Optional<CourseSectionMeeting> findByIdAndCourseSectionIdAndDeletedFalse(Long id, Long sectionId);

    @EntityGraph(attributePaths = {"courseSection", "courseSection.course", "courseSection.course.faculty", "courseSection.course.department",
            "courseSection.lecture", "courseSection.semester", "courseSection.academicTerm", "courseSection.academicTerm.semester",
            "room", "room.building", "room.building.site"})
    Optional<CourseSectionMeeting> findByIdAndDeletedFalse(long id);

    @Override
    @EntityGraph(attributePaths = {"courseSection", "courseSection.course", "courseSection.course.faculty", "courseSection.course.department",
            "courseSection.lecture", "courseSection.semester", "courseSection.academicTerm", "courseSection.academicTerm.semester",
            "room", "room.building", "room.building.site"})
    Page<CourseSectionMeeting> findAll(Specification<CourseSectionMeeting> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"courseSection", "courseSection.course", "courseSection.course.faculty", "courseSection.course.department",
            "courseSection.lecture", "courseSection.semester", "courseSection.academicTerm", "courseSection.academicTerm.semester",
            "room", "room.building", "room.building.site"})
    List<CourseSectionMeeting> findAll(Specification<CourseSectionMeeting> specification);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select m from CourseSectionMeeting m where m.id = :id and m.deleted = false")
    Optional<CourseSectionMeeting> findLiveForUpdate(@Param("id") long id);

    /** Student views: published meetings of the given sections with the room, building and campus for display. */
    @Query("""
            select m from CourseSectionMeeting m
            left join fetch m.room r left join fetch r.building b left join fetch b.site
            where m.courseSection.id in :sectionIds and m.status = :status and m.deleted = false
            order by m.dayOfWeek asc, m.startTime asc, m.id asc
            """)
    List<CourseSectionMeeting> findForSectionsWithStatus(@Param("sectionIds") Collection<Long> sectionIds,
            @Param("status") TimetableMeetingStatus status);

    default List<CourseSectionMeeting> findPublishedForSections(Collection<Long> sectionIds) {
        return findForSectionsWithStatus(sectionIds, TimetableMeetingStatus.PUBLISHED);
    }

    // Conflict candidates: published meetings that use the resource either by their weekly rule inside the date window
    // or through a rescheduled occurrence inside it. Occurrence-level overlap is decided in Java (see D56 on LocalTime).
    @Query("""
            select distinct m from CourseSectionMeeting m left join m.room r
            where m.deleted = false and m.status = :status and m.id <> :excludeId
              and ((r.id = :roomId and m.effectiveFrom <= :to and m.effectiveTo >= :from)
                or exists (select 1 from TimetableMeetingException x left join x.replacementRoom rr
                           where x.meeting = m and x.deleted = false and x.replacementDate between :from and :to
                             and coalesce(rr.id, r.id) = :roomId))
            """)
    List<CourseSectionMeeting> findPublishedUsingRoom(@Param("roomId") long roomId, @Param("from") LocalDate from,
            @Param("to") LocalDate to, @Param("excludeId") long excludeId, @Param("status") TimetableMeetingStatus status);

    @Query("""
            select distinct m from CourseSectionMeeting m join m.courseSection s
            where m.deleted = false and m.status = :status and m.id <> :excludeId and s.lecture.id = :lectureId
              and ((m.effectiveFrom <= :to and m.effectiveTo >= :from)
                or exists (select 1 from TimetableMeetingException x
                           where x.meeting = m and x.deleted = false and x.replacementDate between :from and :to))
            """)
    List<CourseSectionMeeting> findPublishedOfLecturer(@Param("lectureId") long lectureId, @Param("from") LocalDate from,
            @Param("to") LocalDate to, @Param("excludeId") long excludeId, @Param("status") TimetableMeetingStatus status);

    @Query("""
            select distinct m from CourseSectionMeeting m
            where m.deleted = false and m.status = :status and m.id <> :excludeId and m.courseSection.id = :sectionId
              and ((m.effectiveFrom <= :to and m.effectiveTo >= :from)
                or exists (select 1 from TimetableMeetingException x
                           where x.meeting = m and x.deleted = false and x.replacementDate between :from and :to))
            """)
    List<CourseSectionMeeting> findPublishedOfSection(@Param("sectionId") long sectionId, @Param("from") LocalDate from,
            @Param("to") LocalDate to, @Param("excludeId") long excludeId, @Param("status") TimetableMeetingStatus status);

    interface StatusCount { TimetableMeetingStatus getStatus(); long getTotal(); }
    @Query("""
            select m.status as status, count(m) as total from CourseSectionMeeting m
            where m.deleted = false and (:termId is null or m.courseSection.academicTerm.id = :termId)
            group by m.status
            """)
    List<StatusCount> countByStatus(@Param("termId") Long termId);
}
