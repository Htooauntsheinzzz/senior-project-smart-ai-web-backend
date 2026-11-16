package com.smartAiUniversityAssistant.seniorproject.feature.assistant.mapper;

import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.KnowledgeAudience;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.KnowledgeSearchHit;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * Reduces academic query results to the fields the model needs to answer. Internal ids (student, enrollment, course,
 * semester, lecturer, faculty, department, program) are dropped; courseSectionId stays because
 * get_section_availability takes it. Null values are kept as null so the model can say a value is unavailable.
 */
@Component
public class AcademicAiToolResultMapper {

    public Map<String, Object> profile(AcademicProfileQueryResult r) {
        return fields("studentCode", r.studentCode(), "fullName", r.fullName(),
                "facultyCode", r.facultyCode(), "facultyNameEn", r.facultyNameEn(), "facultyNameTh", r.facultyNameTh(),
                "departmentCode", r.departmentCode(), "departmentName", r.departmentName(),
                "programCode", r.programCode(), "programName", r.programName(),
                "semesterNameEn", r.semesterNameEn(), "semesterNameTh", r.semesterNameTh(),
                "semesterAcademicYear", r.semesterAcademicYear(),
                "academicYear", r.academicYear(), "enrollmentYear", r.enrollmentYear());
    }

    public Map<String, Object> enrollment(AcademicEnrollmentQueryResult r) {
        return fields("courseCode", r.courseCode(), "courseName", r.courseName(), "creditHours", r.creditHours(),
                "courseSectionId", r.courseSectionId(), "sectionNumber", r.sectionNumber(),
                "enrollmentStatus", r.enrollmentStatus(),
                "semesterNameEn", r.semesterNameEn(), "semesterNameTh", r.semesterNameTh(),
                "semesterAcademicYear", r.semesterAcademicYear(),
                "lecturerNameEn", r.lecturerNameEn(), "lecturerNameTh", r.lecturerNameTh(),
                "meetings", meetings(r.meetings()));
    }

    public Map<String, Object> course(AcademicCourseQueryResult r) {
        return fields("courseCode", r.courseCode(), "courseName", r.courseName(), "creditHours", r.creditHours(),
                "description", r.description(), "courseTypeName", r.courseTypeName(),
                "courseSectionId", r.courseSectionId(), "sectionNumber", r.sectionNumber(),
                "semesterNameEn", r.semesterNameEn(), "semesterNameTh", r.semesterNameTh(),
                "semesterAcademicYear", r.semesterAcademicYear());
    }

    public Map<String, Object> schedule(AcademicScheduleQueryResult r) {
        return fields("courseCode", r.courseCode(), "courseName", r.courseName(),
                "courseSectionId", r.courseSectionId(), "sectionNumber", r.sectionNumber(),
                "semesterNameEn", r.semesterNameEn(), "semesterNameTh", r.semesterNameTh(),
                "semesterAcademicYear", r.semesterAcademicYear(),
                "lecturerNameEn", r.lecturerNameEn(), "lecturerNameTh", r.lecturerNameTh(),
                "meetings", meetings(r.meetings()));
    }

    // The published timetable (spec 40) is the only source of class times and rooms; sections carry no free text.
    private static List<Map<String, Object>> meetings(List<AcademicMeetingQueryResult> meetings) {
        return meetings == null ? List.of() : meetings.stream().map(m -> fields("meetingType", m.meetingType(),
                "dayOfWeek", m.dayOfWeek(), "startTime", time(m.startTime()), "endTime", time(m.endTime()),
                "effectiveFrom", text(m.effectiveFrom()), "effectiveTo", text(m.effectiveTo()),
                "roomNumber", m.roomNumber(), "buildingName", m.buildingName())).toList();
    }

    private static String time(java.time.LocalTime value) { return value == null ? null : value.toString(); }
    private static String text(Object value) { return value == null ? null : value.toString(); }

    public Map<String, Object> courseSection(AcademicCourseSectionQueryResult r) {
        return fields("courseCode", r.courseCode(), "courseName", r.courseName(),
                "courseSectionId", r.courseSectionId(), "sectionNumber", r.sectionNumber(),
                "semesterNameEn", r.semesterNameEn(), "semesterNameTh", r.semesterNameTh(),
                "semesterAcademicYear", r.semesterAcademicYear(),
                "lecturerNameEn", r.lecturerNameEn(), "lecturerNameTh", r.lecturerNameTh(),
                "enrollmentStatus", r.enrollmentStatus(), "meetings", meetings(r.meetings()));
    }

    public Map<String, Object> lecturer(AcademicLecturerQueryResult r) {
        return fields("lecturerNameEn", r.lecturerNameEn(), "lecturerNameTh", r.lecturerNameTh(),
                "lecturerNickname", r.lecturerNickname(),
                "courseCode", r.courseCode(), "courseName", r.courseName(),
                "courseSectionId", r.courseSectionId(), "sectionNumber", r.sectionNumber(),
                "semesterNameEn", r.semesterNameEn(), "semesterNameTh", r.semesterNameTh(),
                "semesterAcademicYear", r.semesterAcademicYear());
    }

    public Map<String, Object> courseInformation(CourseInformationQueryResult r) {
        return fields("courseCode", r.courseCode(), "courseName", r.courseName(), "creditHours", r.creditHours(),
                "description", r.description(), "courseTypeName", r.courseTypeName(),
                "prerequisiteCourses", r.prerequisiteCourses(),
                "maximumStudentsPerSection", r.maximumStudentsPerSection(),
                "recommendedAcademicYear", r.recommendedAcademicYear(), "active", r.active(),
                "facultyCode", r.facultyCode(), "facultyNameEn", r.facultyNameEn(), "facultyNameTh", r.facultyNameTh(),
                "departmentCode", r.departmentCode(), "departmentName", r.departmentName(),
                "programCode", r.programCode(), "programName", r.programName());
    }

    public Map<String, Object> sectionAvailability(SectionAvailabilityQueryResult r) {
        return fields("courseSectionId", r.courseSectionId(), "courseCode", r.courseCode(), "courseName", r.courseName(),
                "sectionNumber", r.sectionNumber(), "capacity", r.capacity(), "occupiedSeats", r.occupiedSeats(),
                "availableSeats", r.availableSeats(), "isFull", r.isFull(), "sectionStatus", r.sectionStatus(),
                "semesterNameEn", r.semesterNameEn(), "semesterNameTh", r.semesterNameTh(),
                "semesterAcademicYear", r.semesterAcademicYear());
    }

    public List<Map<String, Object>> candidates(List<AcademicCourseCandidate> candidates) {
        return candidates.stream().map(c -> fields("courseCode", c.courseCode(), "courseName", c.courseName())).toList();
    }

    /**
     * Step 15A: one passage of search_university_knowledge, exactly as the model receives it. A key without a value is
     * left out; no id, score, rank, file name, scope or neighbour flag is included. pages only for PDF hits ("12" or
     * "12-13"). Step 15B: appliesTo says whom the passage applies to, in words the model reads.
     */
    public Map<String, Object> knowledgePassage(KnowledgeSearchHit hit) {
        var map = new LinkedHashMap<String, Object>();
        map.put("documentTitle", hit.title());
        if (hit.categoryName() != null) map.put("category", hit.categoryName());
        map.put("academicYear", hit.academicYearLabel());
        if (hit.audience() != null) map.put("appliesTo", appliesTo(hit.audience()));
        if (hit.sectionTitle() != null) map.put("section", hit.sectionTitle());
        if ("PAGE".equals(hit.unitType()))
            map.put("pages", hit.pageStart() == hit.pageEnd() ? String.valueOf(hit.pageStart()) : hit.pageStart() + "-" + hit.pageEnd());
        map.put("text", hit.content());
        return map;
    }

    /** Step 16A: the progress totals, null values kept; no id, name, code, course or grade. */
    public Map<String, Object> progress(AcademicProgressQueryResult r) {
        return fields("cgpa", r.cgpa(), "creditsEarned", r.creditsEarned(), "currentCourses", r.currentCourses(),
                "currentEnrolledCredits", r.currentEnrolledCredits(), "studyYear", r.studyYear(), "academicYear", r.academicYear(),
                "dataStatus", r.dataStatus());
    }

    static String appliesTo(KnowledgeAudience audience) {
        return switch (audience) {
            case PROGRAM -> "your program";
            case DEPARTMENT -> "your department";
            case UNIVERSITY -> "all students";
        };
    }

    // LinkedHashMap keeps null values and a stable field order.
    private static Map<String, Object> fields(Object... pairs) {
        var map = new LinkedHashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], pairs[i + 1]);
        return map;
    }
}
