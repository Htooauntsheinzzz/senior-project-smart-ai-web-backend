package com.smartAiUniversityAssistant.seniorproject.feature.coursesection.mapper;

import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.entity.*;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto.TimetableMeetingResponse;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.entity.AcademicTerm;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.enums.TimetableMeetingStatus;
import java.time.*;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class CourseSectionMapper {
    // The legacy per-section meeting shape, built from the timetable's own response.
    public CourseSectionMeetingResponse meeting(TimetableMeetingResponse m) {
        var location = m.location();
        return new CourseSectionMeetingResponse(m.id(), m.courseSection().id(), m.meetingType().name(), m.dayOfWeek(), m.dayName(),
                m.startTime(), m.endTime(), m.effectiveFrom(), m.effectiveTo(), location.roomId(), location.roomNumber(),
                location.buildingName(), location.legacyRoomLabel(), m.status().name(), m.status() == TimetableMeetingStatus.PUBLISHED,
                m.notes(), m.createdAt(), m.updatedAt());
    }

    // enrolledCount counts PENDING + ACTIVE enrollments; isFull is enrolledCount >= capacity.
    public CourseSectionResponse response(CourseSection s, long enrolled) {
        var course = s.getCourse();
        var lecture = s.getLecture();
        var semester = s.getSemester();
        return new CourseSectionResponse(s.getId(),
                course.getId(), course.getCourseCode(), course.getCourseName(),
                s.getSectionNumber(), s.getCapacity(), enrolled, enrolled >= s.getCapacity(),
                lecture.getId(), lecture.getLectureNameTh(), lecture.getLectureNameEn(), lecture.getLectureNickname(),
                semester.getId(), semester.getSemesterNameTh(), semester.getSemesterNameEn(),
                termId(s.getAcademicTerm()), termCode(s.getAcademicTerm()), termLabel(s.getAcademicTerm()),
                s.getStatus(), s.getCreatedBy(), instant(s.getCreatedAt()), s.getUpdatedBy(), instant(s.getUpdatedAt()));
    }

    public CourseSectionListResponse listResponse(CourseSection s, long enrolled) {
        var course = s.getCourse();
        var lecture = s.getLecture();
        var semester = s.getSemester();
        return new CourseSectionListResponse(s.getId(),
                course.getId(), course.getCourseCode(), course.getCourseName(),
                s.getSectionNumber(),
                lecture.getId(), lecture.getLectureNameEn(), lecture.getLectureNameTh(),
                s.getCapacity(), enrolled, enrolled >= s.getCapacity(),
                semester.getId(), semester.getSemesterNameEn(), semester.getSemesterNameTh(),
                termId(s.getAcademicTerm()), termCode(s.getAcademicTerm()), termLabel(s.getAcademicTerm()),
                s.getStatus(), instant(s.getCreatedAt()), instant(s.getUpdatedAt()));
    }

    public CourseSectionPageResponse page(Page<CourseSection> page, CourseSectionListQuery query, Map<Long, Long> enrolled) {
        return new CourseSectionPageResponse(page.getContent().stream()
                .map(s -> listResponse(s, enrolled.getOrDefault(s.getId(), 0L))).toList(),
                query.page(), query.size(), page.getTotalElements(), page.getTotalPages(),
                query.page() == 0, query.page() >= page.getTotalPages() - 1, query.sort());
    }

    private Instant instant(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC); }
    private static Long termId(AcademicTerm t) { return t == null ? null : t.getId(); }
    private static String termCode(AcademicTerm t) { return t == null ? null : t.getAcademicYearCode(); }
    private static String termLabel(AcademicTerm t) { return t == null ? null : t.getAcademicYearLabel(); }
}
