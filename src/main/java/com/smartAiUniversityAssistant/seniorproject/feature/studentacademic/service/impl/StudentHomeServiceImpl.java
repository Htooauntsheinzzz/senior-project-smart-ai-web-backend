package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service.impl;

import com.smartAiUniversityAssistant.seniorproject.config.StudentAcademicProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentHomeServiceImpl implements StudentHomeService {
    private final StudentAcademicService academics;
    private final AcademicScheduleService schedule;
    private final AcademicDeadlineService deadlines;
    private final StudentAcademicProperties properties;

    public StudentHomeServiceImpl(StudentAcademicService academics, AcademicScheduleService schedule,
            AcademicDeadlineService deadlines, StudentAcademicProperties properties) {
        this.academics = academics;
        this.schedule = schedule;
        this.deadlines = deadlines;
        this.properties = properties;
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public StudentHomeSummaryResponse getSummary(long studentId) {
        var profile = academics.getProfile(studentId);
        var info = new StudentHomeSummaryResponse.StudentInfo(profile.fullName(),
                profile.program() == null ? null : profile.program().nameEn(), profile.studyYear());
        return new StudentHomeSummaryResponse(info, schedule.getNextClass(studentId).orElse(null),
                deadlines.getDeadlines(studentId, properties.homeDeadlineLimit(), null, null));
    }
}
