package com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding;

import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.GroundingFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** Spec §8, §12, §15, §49-52: null fields and verified empty results never become invented values. */
@ExtendWith(OutputCaptureExtension.class)
class AcademicMissingDataTests extends GroundingServiceTestSupport {

    @Test
    void test2MissingLecturerHonestAnswerIsReturned() {
        when(toolExecutor.execute(any())).thenReturn(ok("get_my_course_lecturer", lecturer("CS301", "SEC-01", null)));
        model(tools(call("get_my_course_lecturer", "courseIdentifier", "CS301")),
                "The lecturer for your CS301 section is not currently available.");
        assertThat(ask("Who teaches CS301?")).isEqualTo("The lecturer for your CS301 section is not currently available.");
    }

    @Test
    void test2FabricatedLecturerIsRejected(CapturedOutput output) {
        when(toolExecutor.execute(any())).thenReturn(ok("get_my_course_lecturer", lecturer("CS301", "SEC-01", null)));
        model(tools(call("get_my_course_lecturer", "courseIdentifier", "CS301")), "CS301 is taught by Dr. Jane Lee.");
        String answer = ask("Who teaches CS301?");
        assertThat(answer).isEqualTo("The lecturer for your CS301 section is not currently available.").doesNotContain("Jane Lee");
        assertThat(output.getOut()).contains("ASSISTANT_GROUNDING_REJECTED").contains("outcome=UNSUPPORTED_ACADEMIC_CLAIM issues=[LECTURER]")
                .contains("event=GROUNDING_UNSUPPORTED_CLAIM").contains("fallbackUsed=true").doesNotContain("Jane Lee");
    }

    @Test
    void test3MissingRoomIsNeverInvented() {
        when(toolExecutor.execute(any())).thenReturn(ok("get_my_course_section", section("CS301", "SEC-01", null, "Tuesday 09:00-11:45", "Dr. John Smith")));
        model(tools(call("get_my_course_section", "courseIdentifier", "CS301")), "Your class is in Room 11-402.");
        String answer = ask("Where is my CS301 class?");
        assertThat(answer).contains("Tuesday 09:00-11:45 (lecture), room not currently available").doesNotContain("11-402");
    }

    @Test
    void missingScheduleIsReportedAsUnavailable() {
        when(toolExecutor.execute(any())).thenReturn(ok("get_my_schedule", List.of(map("courseCode", "CSC460", "courseName", "Cloud Computing",
                "sectionNumber", "SEC-01", "meetings", List.of(), "lecturerNameEn", "Dr. Jane Lee"))));
        model(tools(call("get_my_schedule")), "CSC460 meets on Monday 08:00-10:00 in 7-101.");
        assertThat(ask("What is my schedule?")).isEqualTo("Your recorded timetable:\n1. CSC460 — Cloud Computing (section SEC-01): "
                + "timetable not currently available").doesNotContain("Monday").doesNotContain("7-101");
    }

    @Test
    void test5VerifiedEmptyEnrollmentsAreAnsweredByTheBackend(CapturedOutput output) {
        when(toolExecutor.execute(any())).thenReturn(ok("get_my_enrollments", List.of()));
        model(tools(call("get_my_enrollments")), "You are enrolled in CS301 and CSC452.");
        assertThat(ask("What courses am I enrolled in?")).isEqualTo("No current active enrollments were found for your account.");
        // The empty result never went back to the model to be filled in.
        assertThat(sent(1)).hasSize(1);
        assertThat(output.getOut()).contains("event=GROUNDING_MISSING_DATA academicIntent=GET_MY_ENROLLMENTS toolNames=[get_my_enrollments] "
                + "groundingStatus=GROUNDED evidenceStatus=[VERIFIED_EMPTY] validationOutcome=- fallbackUsed=false");
    }

    @Test
    void rule9MissingSemesterIsNotInferred() {
        when(toolExecutor.execute(any())).thenReturn(ok("get_my_academic_profile", profile("Computer Science", null)));
        model(tools(call("get_my_academic_profile")), "You are in Semester 2 of the 2026 academic year.");
        assertThat(ask("What semester am I in?")).contains("- Semester: not currently available").doesNotContain("Semester 2");
    }

    @Test
    void notEnrolledAndUnknownCoursesAreExplainedWithoutAnotherProviderCall() {
        when(toolExecutor.execute(any())).thenReturn(failed("get_my_course_lecturer", "STUDENT_COURSE_ENROLLMENT_NOT_FOUND"));
        model(tools(call("get_my_course_lecturer", "courseIdentifier", "CSC470")), "Dr. John Smith teaches CSC470.");
        assertThat(ask("Who teaches my CSC470 class?")).isEqualTo("No matching enrollment was found for your account for CSC470: "
                + "you are not currently enrolled in that course.");
        assertThat(sent(1)).hasSize(1);
    }

    @Test
    void unknownCourseIsNotFound() {
        when(toolExecutor.execute(any())).thenReturn(failed("get_course_information", "COURSE_NOT_FOUND"));
        model(tools(call("get_course_information", "courseIdentifier", "XYZ999")), "XYZ999 is worth 3 credits.");
        assertThat(ask("How many credits is XYZ999?")).isEqualTo("I could not find a course matching \"XYZ999\" in the university catalogue.");
    }
}
