package com.smartAiUniversityAssistant.seniorproject.feature.assistant;

import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.exception.*;
import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.service.AcademicQueryService;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.AcademicAiToolDefinition;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.AcademicAiToolResult;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.LlmToolCall;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.mapper.AcademicAiToolResultMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl.AcademicAiToolExecutorImpl;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl.AcademicAiToolRegistryImpl;
import com.smartAiUniversityAssistant.seniorproject.feature.course.exception.CourseNotFoundException;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.exception.CourseSectionNotFoundException;
import com.smartAiUniversityAssistant.seniorproject.feature.student.exception.StudentAccountNotActiveException;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Registry allowlist, explicit tool-to-service mapping, argument validation and safe result shaping. */
class AcademicAiToolExecutorTests {
    private static final Set<String> TOOLS = Set.of("get_my_academic_profile", "get_my_enrollments", "get_my_courses",
            "get_my_schedule", "get_my_course_section", "get_my_course_lecturer", "get_course_information",
            "get_section_availability");
    private final AcademicQueryService academicQuery = mock(AcademicQueryService.class);
    private final AcademicAiToolRegistryImpl registry = new AcademicAiToolRegistryImpl();
    private final AcademicAiToolExecutorImpl executor = new AcademicAiToolExecutorImpl(registry, academicQuery,
            new AcademicAiToolResultMapper());

    private AcademicAiToolResult run(String name, Map<String, Object> args) {
        return executor.execute(new LlmToolCall("call-1", name, args));
    }
    private static Map<String, Object> course(Object identifier) {
        var args = new HashMap<String, Object>();
        args.put("courseIdentifier", identifier);
        return args;
    }
    private static Map<String, Object> section(Object id) {
        var args = new HashMap<String, Object>();
        args.put("courseSectionId", id);
        return args;
    }

    @Test
    void registryContainsExactlyTheEightReadOnlyTools() {
        assertThat(registry.getAvailableTools()).extracting(AcademicAiToolDefinition::name).containsExactlyInAnyOrderElementsOf(TOOLS);
        TOOLS.forEach(name -> assertThat(registry.isSupported(name)).isTrue());
        for (String name : Arrays.asList("delete_student", "run_sql", "get_all_students", "change_grade", "GET_MY_COURSES",
                "getMyCourses", "", null))
            assertThat(registry.isSupported(name)).as(String.valueOf(name)).isFalse();
    }

    @Test
    void toolSchemasAreClosedAndNeverMentionAStudentId() {
        for (AcademicAiToolDefinition tool : registry.getAvailableTools()) {
            assertThat(tool.description()).isNotBlank();
            assertThat(tool.parameterSchema()).containsEntry("type", "object").containsEntry("additionalProperties", false);
            assertThat(tool.parameterSchema().toString()).doesNotContainIgnoringCase("studentId");
        }
        assertThat(schemaProperties("get_my_enrollments")).isEmpty();
        assertThat(schemaProperties("get_my_course_section")).containsOnlyKeys("courseIdentifier");
        assertThat(schemaProperties("get_section_availability")).containsOnlyKeys("courseSectionId");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> schemaProperties(String name) {
        return (Map<String, Object>) registry.getAvailableTools().stream().filter(t -> t.name().equals(name)).findFirst()
                .orElseThrow().parameterSchema().get("properties");
    }

    @Test
    void unsupportedToolsAreRejectedWithoutTouchingTheService() {
        for (String name : Arrays.asList("delete_student", "run_sql", "get_all_students", "java.lang.Runtime", null)) {
            AcademicAiToolResult result = run(name, Map.of());
            assertThat(result.success()).isFalse();
            assertThat(result.errorCode()).isEqualTo("UNSUPPORTED_AI_TOOL");
            // The model-chosen name is not echoed back.
            assertThat(result.payload().toString()).doesNotContain(String.valueOf(name));
        }
        assertThat(executor.execute(null).errorCode()).isEqualTo("UNSUPPORTED_AI_TOOL");
        verifyNoInteractions(academicQuery);
    }

    @Test
    void profileMapsWithoutInternalIdsOrContactDetails() {
        when(academicQuery.getMyAcademicProfile()).thenReturn(new AcademicProfileQueryResult(15L, "STD-TEST-001", "Test Student",
                "std@rsu.ac.th", 1L, "IT", "Information Technology", "เทคโนโลยีสารสนเทศ", 2L, "CS", "Computer Science",
                3L, "BSC-CS", "Computer Science", 4L, 2026, "Semester 1", "ภาคการศึกษาที่ 1", 2, 2025));
        AcademicAiToolResult result = run("get_my_academic_profile", Map.of());
        assertThat(result.success()).isTrue();
        @SuppressWarnings("unchecked") var data = (Map<String, Object>) result.data();
        assertThat(data).containsEntry("studentCode", "STD-TEST-001").containsEntry("facultyNameEn", "Information Technology")
                .containsEntry("departmentName", "Computer Science").containsEntry("programName", "Computer Science")
                .containsEntry("semesterNameEn", "Semester 1").containsEntry("academicYear", 2)
                .doesNotContainKeys("studentId", "universityEmail", "facultyId", "departmentId", "programId", "semesterId");
        verify(academicQuery).getMyAcademicProfile();
    }

    @Test
    void listToolsMapToTheirServiceMethodsAndKeepOnlyTheNeededFields() {
        when(academicQuery.getMyEnrollments()).thenReturn(List.of(new AcademicEnrollmentQueryResult(900L, "ACTIVE",
                LocalDate.of(2026, 8, 15), 10L, "CS301", "Data Structures", 3, 15L, "SEC-01", 4L, 2026, "Semester 1", "ภาค 1",
                7L, "Dr. John Smith", "จอห์น", "11-402", "Tuesday 09:00-11:45")));
        when(academicQuery.getMyCourses()).thenReturn(List.of(new AcademicCourseQueryResult(10L, "CS301", "Data Structures", 3,
                "desc", 1, "CORE", 15L, "SEC-01", 4L, 2026, "Semester 1", "ภาค 1")));
        when(academicQuery.getMySchedule()).thenReturn(List.of(new AcademicScheduleQueryResult(10L, "CS301", "Data Structures",
                15L, "SEC-01", 4L, 2026, "Semester 1", "ภาค 1", null, null, 7L, "Dr. John Smith", "จอห์น")));

        AcademicAiToolResult enrollments = run("get_my_enrollments", Map.of());
        assertThat(enrollments.resultCount()).isEqualTo(1);
        assertThat(first(enrollments)).containsEntry("courseCode", "CS301").containsEntry("sectionNumber", "SEC-01")
                .containsEntry("enrollmentStatus", "ACTIVE").containsEntry("courseSectionId", 15L).containsEntry("room", "11-402")
                .doesNotContainKeys("enrollmentId", "courseId", "semesterId", "lecturerId", "studentId", "enrollmentDate");
        assertThat(first(run("get_my_courses", Map.of()))).containsEntry("creditHours", 3).containsEntry("courseTypeName", "CORE")
                .doesNotContainKeys("courseId", "courseType", "semesterId");
        // Null schedule and room are preserved as null, not dropped or replaced.
        assertThat(first(run("get_my_schedule", Map.of()))).containsEntry("schedule", null).containsEntry("room", null)
                .containsKey("schedule").doesNotContainKeys("lecturerId", "courseId");

        verify(academicQuery).getMyEnrollments();
        verify(academicQuery).getMyCourses();
        verify(academicQuery).getMySchedule();
        verifyNoMoreInteractions(academicQuery);
    }

    @Test
    void emptyListsAreSuccessfulEmptyResults() {
        when(academicQuery.getMyEnrollments()).thenReturn(List.of());
        AcademicAiToolResult result = run("get_my_enrollments", Map.of());
        assertThat(result.success()).isTrue();
        assertThat(result.payload()).isEqualTo(Map.of("success", true, "data", List.of()));
    }

    @Test
    void courseToolsMapToTheirServiceMethodsWithTheTrimmedIdentifier() {
        when(academicQuery.getMyCourseSection("CS301")).thenReturn(new AcademicCourseSectionQueryResult(10L, "CS301",
                "Data Structures", 15L, "SEC-01", 4L, 2026, "Semester 1", "ภาค 1", "11-402", "Tuesday 09:00-11:45", 7L,
                "Dr. John Smith", "จอห์น", "ACTIVE"));
        when(academicQuery.getMyCourseLecturer("CS301")).thenReturn(new AcademicLecturerQueryResult(7L, null, null, null,
                10L, "CS301", "Data Structures", 15L, "SEC-01", 4L, 2026, "Semester 1", "ภาค 1"));
        when(academicQuery.getCourseInformation("CS301")).thenReturn(new CourseInformationQueryResult(10L, "CS301",
                "Data Structures", 3, "desc", 1, "CORE", "CS201", 40, 2, true, 1L, "IT", "IT", "ไอที", 2L, "CS", "CS", null, null, null));

        assertThat(data(run("get_my_course_section", course("  CS301 ")))).containsEntry("room", "11-402")
                .containsEntry("courseSectionId", 15L).doesNotContainKeys("courseId", "lecturerId");
        // A missing lecturer stays null so the model cannot be handed a made-up name.
        assertThat(data(run("get_my_course_lecturer", course("CS301")))).containsEntry("lecturerNameEn", null)
                .containsKey("lecturerNameEn").doesNotContainKey("lecturerId");
        assertThat(data(run("get_course_information", course("CS301")))).containsEntry("creditHours", 3)
                .containsEntry("prerequisiteCourses", "CS201").containsEntry("programName", null).doesNotContainKeys("courseId", "facultyId");
        verify(academicQuery).getMyCourseSection("CS301");
        verify(academicQuery).getMyCourseLecturer("CS301");
        verify(academicQuery).getCourseInformation("CS301");
    }

    @Test
    void availabilityMapsToTheServiceWithAWholePositiveId() {
        when(academicQuery.getSectionAvailability(15L)).thenReturn(new SectionAvailabilityQueryResult(15L, 10L, "CS301",
                "Data Structures", "SEC-01", 40, 35, 5, false, "ACTIVE", 4L, 2026, "Semester 1", "ภาค 1"));
        for (Object id : List.of(15, 15L, 15.0, BigInteger.valueOf(15)))
            assertThat(data(run("get_section_availability", section(id)))).containsEntry("capacity", 40)
                    .containsEntry("occupiedSeats", 35L).containsEntry("availableSeats", 5L).containsEntry("isFull", false)
                    .doesNotContainKeys("courseId", "semesterId");
        verify(academicQuery, times(4)).getSectionAvailability(15L);
    }

    @Test
    void invalidArgumentsAreRejectedWithoutCallingTheService() {
        Map<String, Map<String, Object>> invalid = new LinkedHashMap<>();
        invalid.put("unknown argument on a no-argument tool", Map.of("studentId", 15));
        invalid.put("malformed (null) arguments", null);
        invalid.put("blank identifier", course("   "));
        invalid.put("empty identifier", course(""));
        invalid.put("null identifier", course(null));
        invalid.put("numeric identifier", course(301));
        invalid.put("oversized identifier", course("C".repeat(101)));
        invalid.put("missing identifier", Map.of());
        invalid.put("studentId next to identifier", Map.of("courseIdentifier", "CS301", "studentId", 100, "role", "ADMIN"));
        invalid.put("zero section", section(0));
        invalid.put("negative section", section(-10));
        invalid.put("null section", section(null));
        invalid.put("string section", section("15"));
        invalid.put("fractional section", section(15.5));
        invalid.put("overflowing section", section(new BigInteger("99999999999999999999")));
        invalid.put("studentId next to section", Map.of("courseSectionId", 15, "studentId", 100));
        invalid.forEach((label, args) -> {
            String tool = label.contains("section") ? "get_section_availability"
                    : label.contains("no-argument") ? "get_my_courses"
                    : label.contains("malformed") ? "get_my_enrollments" : "get_my_course_lecturer";
            AcademicAiToolResult result = run(tool, args);
            assertThat(result.success()).as(label).isFalse();
            assertThat(result.errorCode()).as(label).isEqualTo("INVALID_AI_TOOL_ARGUMENTS");
        });
        // Exactly 100 characters is still accepted.
        when(academicQuery.getMyCourseSection("C".repeat(100))).thenReturn(new AcademicCourseSectionQueryResult(10L, "CS301",
                "Data Structures", 15L, "SEC-01", 4L, 2026, "Semester 1", "ภาค 1", null, null, 7L, null, null, "ACTIVE"));
        assertThat(run("get_my_course_section", course("C".repeat(100))).success()).isTrue();
        verify(academicQuery).getMyCourseSection("C".repeat(100));
        verifyNoMoreInteractions(academicQuery);
    }

    @Test
    void expectedAcademicErrorsBecomeSafeStructuredResults() {
        when(academicQuery.getMyCourseLecturer("Programming")).thenThrow(new AcademicCourseAmbiguousException(List.of(
                new AcademicCourseCandidate(1L, "CS101", "Programming I"), new AcademicCourseCandidate(2L, "CS102", "Programming II"))));
        when(academicQuery.getMyCourseSection("CS999")).thenThrow(new StudentCourseEnrollmentNotFoundException());
        when(academicQuery.getCourseInformation("INVALID999")).thenThrow(new CourseNotFoundException());
        when(academicQuery.getSectionAvailability(404L)).thenThrow(new CourseSectionNotFoundException());
        when(academicQuery.getMyCourseLecturer("bad\u0000id")).thenThrow(new AcademicQueryValidationException());
        when(academicQuery.getMySchedule()).thenThrow(new DataAccessResourceFailureException("connection to db-host:5432 refused"));

        var ambiguous = run("get_my_course_lecturer", course("Programming")).payload();
        assertThat(ambiguous).containsEntry("success", false).containsEntry("code", "ACADEMIC_COURSE_AMBIGUOUS")
                .containsEntry("candidates", List.of(Map.of("courseCode", "CS101", "courseName", "Programming I"),
                        Map.of("courseCode", "CS102", "courseName", "Programming II")));
        assertThat(run("get_my_course_section", course("CS999")).errorCode()).isEqualTo("STUDENT_COURSE_ENROLLMENT_NOT_FOUND");
        assertThat(run("get_course_information", course("INVALID999")).errorCode()).isEqualTo("COURSE_NOT_FOUND");
        assertThat(run("get_section_availability", section(404)).errorCode()).isEqualTo("COURSE_SECTION_NOT_FOUND");
        assertThat(run("get_my_course_lecturer", course("bad\u0000id")).errorCode()).isEqualTo("INVALID_AI_TOOL_ARGUMENTS");
        var dbFailure = run("get_my_schedule", Map.of()).payload();
        assertThat(dbFailure).containsEntry("code", "AI_TOOL_EXECUTION_FAILED");
        // No SQL, host, class name or stack detail reaches the model.
        assertThat(dbFailure.toString()).doesNotContain("db-host").doesNotContain("Exception").doesNotContain("5432");
    }

    @Test
    void identityAndEligibilityFailuresAreNotSwallowed() {
        when(academicQuery.getMyCourses()).thenThrow(new AccessDeniedException("Access is denied."));
        when(academicQuery.getMyEnrollments()).thenThrow(new StudentAccountNotActiveException());
        assertThatThrownBy(() -> run("get_my_courses", Map.of())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> run("get_my_enrollments", Map.of())).isInstanceOf(StudentAccountNotActiveException.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(AcademicAiToolResult result) {
        assertThat(result.success()).as(String.valueOf(result.errorCode())).isTrue();
        return (Map<String, Object>) result.data();
    }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> first(AcademicAiToolResult result) {
        assertThat(result.success()).isTrue();
        return ((List<Map<String, Object>>) result.data()).getFirst();
    }
}
