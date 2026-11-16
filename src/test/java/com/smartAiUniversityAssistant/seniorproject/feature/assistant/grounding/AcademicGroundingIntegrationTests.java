package com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding;

import com.smartAiUniversityAssistant.seniorproject.feature.timetable.PublishedMeetingTestData;
import static org.assertj.core.api.Assertions.assertThat;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl.StudentAssistantServiceImpl;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** Spec §48-59 against PostgreSQL: grounded answers, missing data, ambiguity and unsupported schedule reasoning. */
@ExtendWith(OutputCaptureExtension.class)
class AcademicGroundingIntegrationTests extends GroundingIntegrationSupport {
    private static final String A = "STD-TEST-001", B = "STD-TEST-002", C = "STD-TEST-003";

    @Test
    void test1VerifiedLecturerIsAnsweredAndLogged(CapturedOutput output) throws Exception {
        tool("get_my_course_lecturer", "courseIdentifier", "CS301");
        answer(r -> "Your CS301 course is taught by " + ((Map<?, ?>) lastData(r)).get("lecturerNameEn") + ".");
        var body = chat(A, null, "Who teaches CS301?");
        assertThat(body.get("message").stringValue()).isEqualTo("Your CS301 course is taught by Dr. John Smith.");
        // The Student API shape is unchanged: no evidence, status or grounding field is exposed.
        // Step 15A (D76): plus the additive sources array, empty for a database answer.
        assertThat(body.propertyNames()).containsExactlyInAnyOrder("message", "requestId", "conversationId", "model", "createdAt", "sources");
        assertThat(body.get("sources")).isEmpty();
        assertThat(output.getOut()).contains("ASSISTANT_GROUNDING requestId=" + body.get("requestId").stringValue()
                + " event=GROUNDING_VERIFIED academicIntent=GET_MY_COURSE_LECTURER toolNames=[get_my_course_lecturer] "
                + "groundingStatus=GROUNDED evidenceStatus=[VERIFIED] validationOutcome=GROUNDED fallbackUsed=false");
    }

    @Test
    void test4IncorrectCreditsFromTheModelAreReplacedByTheDatabaseValue() throws Exception {
        tool("get_course_information", "courseIdentifier", "CS301");
        answer("CS301 is worth 4 credits.");
        assertThat(ask(A, "How many credits is CS301?"))
                .isEqualTo("CS301 — Data Structures is worth 3 credit hours. Prerequisites: CS201.");
    }

    @Test
    void test3NullRoomInPostgresIsNotInvented() throws Exception {
        tool("get_my_course_section", "courseIdentifier", "CSC460");
        answer("Your CSC460 class is in room 9-101.");
        assertThat(ask(A, "Where is my CSC460 class?")).contains("Your CSC460 classroom information is not currently available.")
                .doesNotContain("Timetable:").doesNotContain("9-101");
    }

    @Test
    void test5StudentWithoutEnrollmentsGetsTheVerifiedEmptyReply() throws Exception {
        tool("get_my_enrollments");
        assertThat(ask(C, "What courses am I enrolled in?")).isEqualTo("No current active enrollments were found for your account.");
        assertThat(sent).hasSize(1);
    }

    @Test
    void test10AmbiguousCourseAsksWithTheStudentsOwnCandidates() throws Exception {
        tool("get_my_course_lecturer", "courseIdentifier", "Programming");
        assertThat(ask(B, "Who teaches my Programming course?")).isEqualTo("I found more than one course matching \"Programming\":\n"
                + "1. CSC452 — Mobile Programming\n2. CSC453 — Web Programming\nWhich course do you mean?");
        assertThat(sent).hasSize(1);
    }

    @Test
    void test11NextClassIsNotGuessedAndTheRecordedTimetableIsShown(CapturedOutput output) throws Exception {
        String answer = ask(A, "What is my next class?");
        assertThat(answer).startsWith("I can't reliably work out your next class")
                .contains("Your recorded timetable:\n1. CS301 — Data Structures (section SEC-01): Tuesday 09:00-11:45 (lecture) in room 11-402, "
                        + PublishedMeetingTestData.BUILDING_NAME)
                .contains("CSC460 — Cloud Computing (section SEC-01): timetable not currently available")
                .doesNotContain("11-500");
        assertThat(sent).isEmpty();
        assertThat(output.getOut()).contains("groundingStatus=UNSUPPORTED").contains("grounding=UNSUPPORTED");
    }

    @Test
    void test12ProgramAnsweredWithoutAnyToolIsNotTreatedAsVerified() throws Exception {
        answer("You are in Computer Science.");
        answer("You are in Computer Science.");
        assertThat(ask(A, "What program am I in?")).isEqualTo(StudentAssistantServiceImpl.UNGROUNDED_FALLBACK_MESSAGE);
        assertThat(sent).extracting(r -> r.requireTool()).containsExactly(false, true);
    }

    @Test
    void missingSemesterIsReportedNotInferred() throws Exception {
        tool("get_my_academic_profile");
        answer("You are in Semester 1 of 2026.");
        assertThat(ask(C, "What semester am I in?")).contains("- Semester: not currently available").doesNotContain("Semester 1");
    }

    @Test
    void sectionAvailabilityMustMatchTheSeatCounts() throws Exception {
        long section = ((Number) fixture.placeholders().get("CS301_SEC01")).longValue();
        tool("get_section_availability", "courseSectionId", section);
        answer("Section " + section + " has 37 of 40 seats available.");
        assertThat(ask(A, "Is section " + section + " full?")).isEqualTo("Section " + section + " has 37 of 40 seats available.");
        tool("get_section_availability", "courseSectionId", section);
        answer("Section " + section + " has only 2 seats left.");
        assertThat(ask(A, "Is section " + section + " full?"))
                .isEqualTo("CS301 section SEC-01 has 37 of 40 seats available (3 taken) and is not full. Section status: ACTIVE.");
    }

    @Test
    void generalQuestionsAreStillAnsweredNormally() throws Exception {
        answer("A credit hour usually means one hour of class per week for a semester.");
        assertThat(ask(A, "What is a credit hour?")).isEqualTo("A credit hour usually means one hour of class per week for a semester.");
    }
}
