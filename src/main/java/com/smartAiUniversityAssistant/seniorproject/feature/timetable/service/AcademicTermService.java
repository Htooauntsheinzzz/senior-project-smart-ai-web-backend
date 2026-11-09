package com.smartAiUniversityAssistant.seniorproject.feature.timetable.service;

import com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;

public interface AcademicTermService {
    TimetablePageResponse<AcademicTermResponse> list(TimetablePageQuery query);
    AcademicTermResponse detail(long id);
    AcademicTermResponse create(AuthenticatedUser actor, AcademicTermRequest request);
    AcademicTermResponse update(AuthenticatedUser actor, long id, AcademicTermRequest request);
    AcademicTermResponse status(AuthenticatedUser actor, long id, AcademicTermStatusRequest request);
    void delete(AuthenticatedUser actor, long id);
}
