package com.smartAiUniversityAssistant.seniorproject.feature.coursesection.service;

import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.dto.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import java.util.List;

public interface CourseSectionMeetingService {
    List<CourseSectionMeetingResponse> list(AuthenticatedUser actor, long sectionId);
    CourseSectionMeetingResponse create(AuthenticatedUser actor, long sectionId, CourseSectionMeetingRequest request);
    CourseSectionMeetingResponse update(AuthenticatedUser actor, long sectionId, long meetingId, CourseSectionMeetingRequest request);
    void delete(AuthenticatedUser actor, long sectionId, long meetingId);
}
