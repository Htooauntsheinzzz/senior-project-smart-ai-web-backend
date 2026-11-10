package com.smartAiUniversityAssistant.seniorproject.feature.enrollment.service;

import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.dto.EnrollmentWindow;
import java.util.*;

/** The published-timetable and due-date rules shared by Admin and Student enrollment (spec file 44). */
public interface EnrollmentWindowService {
    /** One entry per requested Course Section id. */
    Map<Long, EnrollmentWindow> windows(Collection<Long> courseSectionIds);
    /** Throws COURSE_SECTION_NOT_IN_TIMETABLE or ENROLLMENT_DUE_DATE_PASSED (409) when the section takes no new enrollment. */
    void requireOpen(long courseSectionId);
}
