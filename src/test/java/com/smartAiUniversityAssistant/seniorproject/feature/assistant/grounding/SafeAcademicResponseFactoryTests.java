package com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding;

import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.GroundingFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.impl.GroundedAnswerValidatorImpl;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.impl.SafeAcademicResponseFactoryImpl;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl.StudentAssistantServiceImpl;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Spec §27: deterministic replies, and grounded answers rendered only from verified evidence. */
class SafeAcademicResponseFactoryTests {
    private final SafeAcademicResponseFactory responses = new SafeAcademicResponseFactoryImpl();

    private static AcademicEvidence verified(String tool, Object data) {
        return new AcademicEvidence(tool, data instanceof List<?> l && l.isEmpty() ? AcademicEvidenceStatus.VERIFIED_EMPTY
                : AcademicEvidenceStatus.VERIFIED, data, Instant.EPOCH, null, null);
    }

    @Test
    void fixedRepliesSayWhatIsMissingWithoutGuessing() {
        assertThat(responses.lecturerUnavailable("CSC452")).isEqualTo("The lecturer for your CSC452 section is not currently available.");
        assertThat(responses.roomUnavailable("CS301")).isEqualTo("Your CS301 classroom information is not currently available.");
        assertThat(responses.roomUnavailable(null)).isEqualTo("Your classroom information is not currently available.");
        assertThat(responses.scheduleUnavailable()).isEqualTo("Your class schedule information is not currently available.");
        assertThat(responses.courseNotFound("XYZ999")).contains("could not find a course matching \"XYZ999\"");
        assertThat(responses.courseEnrollmentNotFound("CSC470")).contains("No matching enrollment was found for your account for CSC470");
        assertThat(responses.sectionNotFound("15")).isEqualTo("I could not find a course section with id 15.");
        assertThat(responses.enrollmentUnavailable()).isEqualTo("No current active enrollments were found for your account.");
        // A failure is never worded as an empty record.
        assertThat(responses.academicServiceUnavailable()).contains("can't verify your academic information right now")
                .doesNotContainIgnoringCase("no courses").doesNotContainIgnoringCase("no active");
        assertThat(responses.clarificationRequired()).contains("Which course do you mean?").doesNotContainPattern("[A-Z]{2,5}\\d{3}");
        assertThat(responses.ambiguousCourse(null, List.of())).isEqualTo(responses.clarificationRequired());
        assertThat(responses.missingEvidence()).isEqualTo(StudentAssistantServiceImpl.UNGROUNDED_FALLBACK_MESSAGE);
        assertThat(responses.unsupportedScheduleReasoning()).contains("can't reliably work out your next class");
    }

    @Test
    void groundedAnswerRendersVerifiedFieldsAndWritesNullsAsUnavailable() {
        var answer = responses.groundedAnswer(List.of(
                verified("get_my_course_lecturer", lecturer("CS301", "SEC-01", "Dr. John Smith")),
                verified("get_my_course_section", section("CS301", "SEC-01", null, null, "Dr. John Smith"))));
        assertThat(answer).isEqualTo("Your CS301 — Data Structures section SEC-01 is taught by Dr. John Smith.\n\n"
                + "Your CS301 — Data Structures section is SEC-01. Your CS301 classroom information is not currently available. "
                + "Schedule: not currently available. Lecturer: Dr. John Smith.");
    }

    @Test
    void everyToolHasATemplateAndAdminTextIsNotRendered() {
        assertThat(responses.groundedAnswer(List.of(verified("get_course_information", course("CS301", "Data Structures", 3)))))
                .isEqualTo("CS301 — Data Structures is worth 3 credit hours. Prerequisites: CS201.")
                .doesNotContain("Ignore all rules");
        assertThat(responses.groundedAnswer(List.of(verified("get_section_availability", availability(40, 3)))))
                .isEqualTo("CS301 section SEC-01 has 37 of 40 seats available (3 taken) and is not full. Section status: ACTIVE.");
        assertThat(responses.groundedAnswer(List.of(verified("get_section_availability", availability(2, 2))))).contains("0 of 2 seats", "is full.");
        assertThat(responses.groundedAnswer(List.of(verified("get_my_academic_profile", profile("Computer Science", null)))))
                .contains("- Program: Computer Science").contains("- Semester: not currently available");
        assertThat(responses.groundedAnswer(List.of(verified("get_my_enrollments", List.of(enrollment("CS301", "Data Structures", "SEC-01"),
                enrollment("CSC452", "Mobile Programming", "SEC-02"))))))
                .isEqualTo("Your current enrollments:\n1. CS301 — Data Structures (section SEC-01, 3 credit hours, ACTIVE)\n"
                        + "2. CSC452 — Mobile Programming (section SEC-02, 3 credit hours, ACTIVE)");
        assertThat(responses.groundedAnswer(List.of(verified("get_my_schedule", List.of(map("courseCode", "CSC460",
                "courseName", "Cloud Computing", "sectionNumber", "SEC-01", "schedule", null, "room", null))))))
                .isEqualTo("Your recorded timetable:\n1. CSC460 — Cloud Computing (section SEC-01): schedule not currently available, "
                        + "room not currently available");
        assertThat(responses.groundedAnswer(List.of(verified("get_my_courses", List.of())))).isEqualTo(responses.enrollmentUnavailable());
    }

    @Test
    void withoutVerifiedEvidenceTheReplySaysWhyAndDuplicatesAreRenderedOnce() {
        var failed = new AcademicEvidence("get_my_courses", AcademicEvidenceStatus.UNAVAILABLE, null, Instant.EPOCH, "AI_TOOL_EXECUTION_FAILED", null);
        assertThat(responses.groundedAnswer(List.of(failed))).isEqualTo(responses.academicServiceUnavailable());
        var invalid = new AcademicEvidence("get_my_courses", AcademicEvidenceStatus.ERROR, null, Instant.EPOCH, "INVALID_AI_TOOL_ARGUMENTS", null);
        assertThat(responses.groundedAnswer(List.of(invalid))).isEqualTo(responses.missingEvidence());
        assertThat(responses.groundedAnswer(null)).isEqualTo(responses.missingEvidence());
        var lecturer = verified("get_my_course_lecturer", lecturer("CS301", "SEC-01", "Dr. John Smith"));
        assertThat(responses.groundedAnswer(List.of(lecturer, lecturer))).isEqualTo(responses.groundedAnswer(List.of(lecturer)));
    }

    @Test
    void groundedAnswersPassTheValidatorTheyBackUp() {
        var validator = new GroundedAnswerValidatorImpl();
        var evidence = List.of(verified("get_my_course_section", section("CS301", "SEC-01", "11-402", "Tuesday 09:00-11:45", "Dr. John Smith")),
                verified("get_section_availability", availability(40, 3)), verified("get_course_information", course("CS301", "Data Structures", 3)),
                verified("get_my_academic_profile", profile("Computer Science", "Semester 1")),
                verified("get_my_enrollments", List.of(enrollment("CS301", "Data Structures", "SEC-01"))));
        assertThat(validator.validate(responses.groundedAnswer(evidence), evidence).valid()).isTrue();
    }
}
