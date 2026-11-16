package com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding;

import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.GroundingFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

/** Spec §32, §54: each fact of a multi-fact answer is grounded on its own; partial data never licenses invention. */
class AcademicMultiFactGroundingTests extends GroundingServiceTestSupport {

    @Test
    void test7LecturerKnownRoomMissing() {
        when(toolExecutor.execute(any())).thenReturn(ok("get_my_course_lecturer", lecturer("CS301", "SEC-01", "Dr. John Smith")),
                ok("get_my_course_section", section("CS301", "SEC-01", null, null, "Dr. John Smith")));
        model(tools(call("get_my_course_lecturer", "courseIdentifier", "CS301"), call("get_my_course_section", "courseIdentifier", "CS301")),
                "CS301 is taught by Dr. John Smith.\nThe classroom information is not currently available.");
        assertThat(ask("Who teaches CS301 and where is the room?"))
                .isEqualTo("CS301 is taught by Dr. John Smith.\nThe classroom information is not currently available.");
    }

    @Test
    void anInventedRoomNextToAVerifiedLecturerIsReplacedByBothVerifiedFacts() {
        when(toolExecutor.execute(any())).thenReturn(ok("get_my_course_lecturer", lecturer("CS301", "SEC-01", "Dr. John Smith")),
                ok("get_my_course_section", section("CS301", "SEC-01", null, null, "Dr. John Smith")));
        model(tools(call("get_my_course_lecturer", "courseIdentifier", "CS301"), call("get_my_course_section", "courseIdentifier", "CS301")),
                "CS301 is taught by Dr. John Smith in room 11-402.");
        String answer = ask("Who teaches CS301 and where is my class?");
        assertThat(answer).contains("is taught by Dr. John Smith").contains("Your CS301 classroom information is not currently available.")
                .doesNotContain("11-402");
    }

    @Test
    void bothFactsVerifiedMayBeCombined() {
        when(toolExecutor.execute(any())).thenReturn(ok("get_my_course_lecturer", lecturer("CS301", "SEC-01", "Dr. John Smith")),
                ok("get_course_information", course("CS301", "Data Structures", 3)));
        model(tools(call("get_my_course_lecturer", "courseIdentifier", "CS301"), call("get_course_information", "courseIdentifier", "CS301")),
                "Dr. John Smith teaches CS301, which is worth 3 credit hours.");
        assertThat(ask("Who teaches CS301 and how many credits is it?")).isEqualTo("Dr. John Smith teaches CS301, which is worth 3 credit hours.");
    }

    @Test
    void oneWrongValueInAMultiFactAnswerRejectsTheWholeAnswer() {
        when(toolExecutor.execute(any())).thenReturn(ok("get_my_course_lecturer", lecturer("CS301", "SEC-01", "Dr. John Smith")),
                ok("get_course_information", course("CS301", "Data Structures", 3)));
        model(tools(call("get_my_course_lecturer", "courseIdentifier", "CS301"), call("get_course_information", "courseIdentifier", "CS301")),
                "Dr. John Smith teaches CS301, which is worth 4 credit hours.");
        assertThat(ask("Who teaches CS301 and how many credits is it?"))
                .isEqualTo("Your CS301 — Data Structures section SEC-01 is taught by Dr. John Smith.\n\n"
                        + "CS301 — Data Structures is worth 3 credit hours. Prerequisites: CS201.");
    }

    @Test
    void aVerifiedCourseAndAnUnenrolledOneAreAnsweredTogether() {
        when(toolExecutor.execute(any())).thenReturn(ok("get_my_course_lecturer", lecturer("CS301", "SEC-01", "Dr. John Smith")),
                failed("get_my_course_lecturer", "STUDENT_COURSE_ENROLLMENT_NOT_FOUND"));
        model(tools(call("get_my_course_lecturer", "courseIdentifier", "CS301"), call("get_my_course_lecturer", "courseIdentifier", "CSC470")),
                "Dr. John Smith teaches your CS301 section. You are not enrolled in CSC470.");
        assertThat(ask("Who teaches CS301 and CSC470?")).isEqualTo("Dr. John Smith teaches your CS301 section. You are not enrolled in CSC470.");
    }
}
