package com.smartAiUniversityAssistant.seniorproject.feature.timetable.mapper;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.entity.*;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.entity.*;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.entity.*;
import java.time.*;
import org.springframework.stereotype.Component;

/** Entity to DTO conversions. Every derived name comes from the related record; nothing is stored twice. */
@Component
public class TimetableMapper {
    public AcademicTermResponse term(AcademicTerm t) {
        var s = t.getSemester();
        return new AcademicTermResponse(t.getId(), s.getId(), s.getSemesterNameEn(), s.getSemesterNameTh(), s.getAcademicYear(),
                t.getAcademicYearCode(), t.getAcademicYearLabel(), t.getStartDate(), t.getEndDate(), t.getStatus(),
                s.getSemesterNameEn() + " / " + t.getAcademicYearCode(), t.getCreatedBy(), instant(t.getCreatedAt()),
                t.getUpdatedBy(), instant(t.getUpdatedAt()));
    }

    public TimetableTermRef termRef(AcademicTerm t) {
        if (t == null) return null;
        var s = t.getSemester();
        return new TimetableTermRef(t.getId(), s.getId(), s.getSemesterNameEn(), s.getSemesterNameTh(), t.getAcademicYearCode(),
                t.getAcademicYearLabel(), t.getStartDate(), t.getEndDate(), t.getStatus());
    }

    public TimetableLocation location(CampusRoom room, String legacyLabel) {
        if (room == null) return new TimetableLocation(null, null, null, null, null, null, null, null, null, null, legacyLabel);
        CampusBuilding b = room.getBuilding();
        CampusSite site = b.getSite();
        return new TimetableLocation(site.getId(), site.getSiteName(), b.getId(), b.getBuildingCode(), b.getBuildingName(),
                room.getId(), room.getRoomNumber(), room.getRoomName(), (int) room.getFloorNumber(), room.getCapacity(), legacyLabel);
    }

    public TimetableMeetingResponse meeting(CourseSectionMeeting m) {
        CourseSection s = m.getCourseSection();
        var course = s.getCourse();
        var lecture = s.getLecture();
        return new TimetableMeetingResponse(m.getId(),
                new TimetableNamedRef(s.getId(), s.getSectionNumber(), s.getStatus()),
                new TimetableNamedRef(course.getId(), course.getCourseCode(), course.getCourseName()),
                termRef(s.getAcademicTerm()),
                new TimetableNamedRef(course.getFaculty().getId(), course.getFaculty().getFacultyCode(), course.getFaculty().getFacultyNameEn()),
                new TimetableNamedRef(course.getDepartment().getId(), course.getDepartment().getDepartmentCode(), course.getDepartment().getDepartmentName()),
                new TimetableNamedRef(lecture.getId(), lecture.getLectureNameTh(), lecture.getLectureNameEn()),
                location(m.getRoom(), m.getRoomLabel()), m.getMeetingType(), m.getDayOfWeek(), DayOfWeek.of(m.getDayOfWeek()).name(),
                m.getStartTime(), m.getEndTime(), m.getEffectiveFrom(), m.getEffectiveTo(), m.getStatus(), m.getNotes(),
                m.getCreatedBy(), instant(m.getCreatedAt()), m.getUpdatedBy(), instant(m.getUpdatedAt()));
    }

    public TimetableMeetingListItem listItem(CourseSectionMeeting m) {
        CourseSection s = m.getCourseSection();
        var course = s.getCourse();
        var lecture = s.getLecture();
        var term = s.getAcademicTerm();
        CampusRoom room = m.getRoom();
        CampusBuilding b = room == null ? null : room.getBuilding();
        return new TimetableMeetingListItem(m.getId(), s.getId(), course.getCourseCode(), course.getCourseName(), s.getSectionNumber(),
                course.getFaculty().getId(), course.getFaculty().getFacultyNameEn(), course.getDepartment().getId(), course.getDepartment().getDepartmentName(),
                lecture.getId(), lecture.getLectureNameEn(), term == null ? null : term.getId(), term == null ? null : term.getAcademicYearLabel(),
                term == null ? s.getSemester().getSemesterNameEn() : term.getSemester().getSemesterNameEn(),
                m.getDayOfWeek(), DayOfWeek.of(m.getDayOfWeek()).name(), m.getStartTime(), m.getEndTime(),
                b == null ? null : b.getSite().getId(), b == null ? null : b.getSite().getSiteName(), b == null ? null : b.getId(),
                b == null ? null : b.getBuildingName(), room == null ? null : room.getId(), room == null ? null : room.getRoomNumber(),
                m.getRoomLabel(), m.getMeetingType(), m.getStatus(), m.getEffectiveFrom(), m.getEffectiveTo());
    }

    public TimetableConflictMeeting conflictMeeting(CourseSectionMeeting m) {
        CourseSection s = m.getCourseSection();
        return new TimetableConflictMeeting(m.getId(), s.getId(), s.getCourse().getCourseCode(), s.getCourse().getCourseName(), s.getSectionNumber(),
                m.getMeetingType(), m.getDayOfWeek(), m.getStartTime(), m.getEndTime(), m.getEffectiveFrom(), m.getEffectiveTo(),
                m.getRoomId(), m.getRoom() == null ? null : m.getRoom().getRoomNumber(), s.getLecture().getId(), s.getLecture().getLectureNameEn());
    }

    public TimetableExceptionResponse exception(TimetableMeetingException x) {
        CampusRoom room = x.getReplacementRoom();
        return new TimetableExceptionResponse(x.getId(), x.getMeeting().getId(), x.getOriginalDate(), x.getExceptionType(),
                x.getReplacementDate(), x.getReplacementStartTime(), x.getReplacementEndTime(), room == null ? null : room.getId(),
                room == null ? null : room.getRoomNumber(), room == null ? null : room.getBuilding().getBuildingName(), x.getReason(),
                x.getCreatedBy(), instant(x.getCreatedAt()), x.getUpdatedBy(), instant(x.getUpdatedAt()));
    }

    public static Instant instant(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC); }
}
