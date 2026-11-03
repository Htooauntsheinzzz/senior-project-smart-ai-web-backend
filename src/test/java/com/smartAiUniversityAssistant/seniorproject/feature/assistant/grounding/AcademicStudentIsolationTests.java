package com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;
import org.junit.jupiter.api.Test;

/** Spec §14, §42, §55, §61: only the token's Student's facts, never another Student's or another request's. */
class AcademicStudentIsolationTests extends GroundingIntegrationSupport {
    private static final String A = "STD-TEST-001", B = "STD-TEST-002";

    // An honest model: repeats each schedule row the tool returned.
    private void scheduleAnswer() {
        tool("get_my_schedule");
        answer(r -> {
            var lines = new ArrayList<String>();
            for (Object row : (List<?>) lastData(r)) {
                var m = (Map<?, ?>) row;
                lines.add(m.get("courseCode") + ": " + (m.get("schedule") == null ? "schedule not available" : m.get("schedule"))
                        + ", room " + (m.get("room") == null ? "not available" : m.get("room")));
            }
            return String.join("\n", lines);
        });
    }

    @Test
    void test14EachStudentSeesOnlyTheirOwnSchedule() throws Exception {
        scheduleAnswer();
        String forA = ask(A, "What is my schedule?");
        scheduleAnswer();
        String forB = ask(B, "What is my schedule?");

        assertThat(forA).contains("CS301: Tuesday 09:00-11:45, room 11-402").contains("CSC452: Friday 09:00-11:45, room 5-201")
                .doesNotContain("11-500").doesNotContain("Monday");
        assertThat(forB).contains("CS301: Monday 13:00-15:45, room 11-500").doesNotContain("11-402").doesNotContain("Tuesday")
                .doesNotContain("CSC460");
    }

    @Test
    void anotherStudentsRoomInTheAnswerIsBlocked() throws Exception {
        tool("get_my_schedule");
        answer("CS301: Tuesday 09:00-11:45, room 11-402.");
        String forB = ask(B, "What is my schedule?");
        assertThat(forB).startsWith("Your recorded timetable:").contains("Monday 13:00-15:45, room 11-500")
                .doesNotContain("11-402").doesNotContain("Tuesday");
    }

    @Test
    void test8OnlyTheStudentsOwnSectionIsUsed() throws Exception {
        tool("get_my_course_section", "courseIdentifier", "CS301");
        answer("You are in CS301 SEC-01 with Dr. John Smith in room 11-402.");
        String answer = ask(B, "Which section am I in for CS301?");
        assertThat(answer).isEqualTo("Your CS301 — Data Structures section is SEC-02. Room: 11-500. Schedule: Monday 13:00-15:45. "
                + "Lecturer: Dr. Jane Doe.");
        // The backend queried Student B's own enrollment: SEC-01's lecturer and room never reached the model.
        assertThat(sent.getLast().toolRounds().getFirst().results().getFirst().result().toString())
                .contains("SEC-02").doesNotContain("SEC-01").doesNotContain("John Smith").doesNotContain("11-402");
    }

    @Test
    void evidenceIsNotReusedAcrossRequestsOrStudents() throws Exception {
        tool("get_my_course_lecturer", "courseIdentifier", "CS301");
        answer("Dr. John Smith teaches your CS301 section.");
        assertThat(ask(A, "Who teaches CS301?")).isEqualTo("Dr. John Smith teaches your CS301 section.");

        tool("get_my_course_lecturer", "courseIdentifier", "CS301");
        answer("Dr. John Smith teaches your CS301 section.");
        assertThat(ask(B, "Who teaches CS301?")).isEqualTo("Your CS301 — Data Structures section SEC-02 is taught by Dr. Jane Doe.");
    }
}
