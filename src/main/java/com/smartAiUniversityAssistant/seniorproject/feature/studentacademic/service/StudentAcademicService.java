package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service;

import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto.*;
import java.util.List;

/**
 * Read-only academic data for one Student. Callers pass the Student id resolved from the validated JWT,
 * never a client-supplied value; status accepts PENDING, ACTIVE, WITHDRAWN, DROPPED or ALL (case-insensitive).
 */
public interface StudentAcademicService {
    StudentAcademicProfileResponse getProfile(long studentId);
    List<StudentAcademicEnrollmentResponse> getEnrollments(long studentId, String status);
    List<StudentAcademicCourseResponse> getCourses(long studentId, String status);
    List<StudentAcademicScheduleResponse> getSchedule(long studentId, String status);
    List<StudentAcademicLecturerResponse> getLecturers(long studentId, String status);
    /** Applies the same eligibility rules as every read above, for callers that read non-private academic data. */
    void requireEligibleStudent(long studentId);
}
