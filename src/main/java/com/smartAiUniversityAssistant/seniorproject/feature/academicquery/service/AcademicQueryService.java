package com.smartAiUniversityAssistant.seniorproject.feature.academicquery.service;

import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.dto.*;
import java.util.List;

/**
 * Read-only academic queries for the Student AI assistant. The Student always comes from the authenticated
 * SecurityContext; no method accepts a student id. "My" lists return ACTIVE enrollments only.
 * courseIdentifier is a course code or name, matched by: exact code, exact name, case-insensitive code,
 * case-insensitive name, then a partial match. Several matches raise ACADEMIC_COURSE_AMBIGUOUS instead of a guess.
 */
public interface AcademicQueryService {
    AcademicProfileQueryResult getMyAcademicProfile();
    List<AcademicEnrollmentQueryResult> getMyEnrollments();
    List<AcademicCourseQueryResult> getMyCourses();
    List<AcademicScheduleQueryResult> getMySchedule();
    AcademicCourseSectionQueryResult getMyCourseSection(String courseIdentifier);
    AcademicLecturerQueryResult getMyCourseLecturer(String courseIdentifier);
    CourseInformationQueryResult getCourseInformation(String courseIdentifier);
    SectionAvailabilityQueryResult getSectionAvailability(Long courseSectionId);
}
