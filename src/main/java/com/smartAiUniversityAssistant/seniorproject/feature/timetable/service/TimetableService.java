package com.smartAiUniversityAssistant.seniorproject.feature.timetable.service;

import com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import java.util.List;

/** Admin timetable management (spec file 42, Parts K to N and G). */
public interface TimetableService {
    TimetableMeetingResponse create(AuthenticatedUser actor, TimetableMeetingRequest request);
    List<TimetableMeetingResponse> createBulk(AuthenticatedUser actor, TimetableBulkMeetingRequest request);
    TimetableMeetingResponse detail(long id);
    TimetablePageResponse<TimetableMeetingListItem> list(TimetablePageQuery query);
    TimetableMeetingResponse update(AuthenticatedUser actor, long id, TimetableMeetingSlot slot);
    void delete(AuthenticatedUser actor, long id);
    TimetableMeetingResponse publish(AuthenticatedUser actor, long id);
    TimetableMeetingResponse cancel(AuthenticatedUser actor, long id);
    /** Back to DRAFT: hides the meeting from students again. Used by the course-section meeting facade only. */
    TimetableMeetingResponse revertToDraft(AuthenticatedUser actor, long id);

    /** The course-section facade: create under a section, keep the legacy label, and publish when asked. */
    TimetableMeetingResponse createForSection(AuthenticatedUser actor, long sectionId, TimetableMeetingSlot slot, String legacyLabel, boolean publish);
    TimetableMeetingResponse updateForSection(AuthenticatedUser actor, long sectionId, long id, TimetableMeetingSlot slot, String legacyLabel, Boolean publish);
    List<TimetableMeetingResponse> listForSection(long sectionId);
    void deleteForSection(AuthenticatedUser actor, long sectionId, long id);

    TimetableCalendarResponse calendar(TimetablePageQuery query);
    TimetableConflictCheckResponse check(TimetableConflictCheckRequest request);
    TimetableConflictListResponse conflicts(TimetablePageQuery query);
    TimetableStatisticsResponse statistics(Long academicTermId);
    TimetableFilterOptionsResponse filterOptions(Long academicTermId, Long facultyId, Long departmentId, Long courseId, Long campusId, Long buildingId);

    List<TimetableExceptionResponse> exceptions(long meetingId);
    TimetableExceptionResponse addException(AuthenticatedUser actor, long meetingId, TimetableExceptionRequest request);
    TimetableExceptionResponse updateException(AuthenticatedUser actor, long meetingId, long exceptionId, TimetableExceptionRequest request);
    void deleteException(AuthenticatedUser actor, long meetingId, long exceptionId);
}
