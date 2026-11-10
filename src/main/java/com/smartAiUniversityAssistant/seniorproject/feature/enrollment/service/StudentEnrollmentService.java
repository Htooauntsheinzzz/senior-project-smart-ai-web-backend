package com.smartAiUniversityAssistant.seniorproject.feature.enrollment.service;

import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto.StudentAcademicEnrollmentResponse;
import java.util.List;

/** Student self-enrollment (spec file 43). The student id always comes from the access token. */
public interface StudentEnrollmentService {
    List<StudentAvailableSectionResponse> availableSections(long studentId);
    StudentAcademicEnrollmentResponse enroll(long studentId, StudentEnrollRequest request);
    StudentAcademicEnrollmentResponse withdraw(long studentId, long enrollmentId);
    /** Locks the student's enrollment: every PENDING request of their semester becomes ACTIVE at once. */
    List<StudentAcademicEnrollmentResponse> lock(long studentId);
    /** Locks one of the student's own PENDING requests: it becomes ACTIVE without an admin. */
    StudentAcademicEnrollmentResponse lockOne(long studentId, long enrollmentId);
}
