package com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding;

import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.GroundingFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.service.AcademicQueryService;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.AssistantTestServices;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.mapper.AcademicAiToolResultMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl.AcademicAiToolExecutorImpl;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl.AcademicAiToolRegistryImpl;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;

/** Spec §9, §53: a failed academic query is a verification failure, never an empty record or a guess. */
@ExtendWith(OutputCaptureExtension.class)
class AcademicToolFailureTests extends GroundingServiceTestSupport {
    private static final String CANNOT_VERIFY = "I can't verify your academic information right now, so I won't guess. "
            + "Please try again in a few minutes.";

    @Test
    void test6AcademicQueryServiceUnavailableThroughTheRealExecutor(CapturedOutput output) {
        // The real executor in front of an AcademicQueryService whose database connection fails.
        var academicQuery = mock(AcademicQueryService.class);
        when(academicQuery.getMyEnrollments()).thenThrow(new DataAccessResourceFailureException("connection refused jdbc:postgresql://db"));
        var executor = new AcademicAiToolExecutorImpl(new AcademicAiToolRegistryImpl(), academicQuery, new AcademicAiToolResultMapper());
        service = AssistantTestServices.service(currentStudent, academics, llm, executor, properties, conversations, Clock.fixed(NOW, ZoneOffset.UTC));
        model(tools(call("get_my_enrollments")), "You have no courses.");

        String answer = ask("What courses am I enrolled in?");

        assertThat(answer).isEqualTo(CANNOT_VERIFY).doesNotContain("no courses").doesNotContain("jdbc");
        assertThat(sent(1)).hasSize(1);
        assertThat(output.getOut()).contains("evidence=UNAVAILABLE").contains("event=GROUNDING_TOOL_FAILED")
                .contains("errorCode=AI_TOOL_EXECUTION_FAILED").doesNotContain("connection refused");
    }

    @Test
    void creditsCannotBeVerifiedWhenTheCatalogueQueryFails() {
        when(toolExecutor.execute(any())).thenReturn(failed("get_course_information", "AI_TOOL_EXECUTION_FAILED"));
        model(tools(call("get_course_information", "courseIdentifier", "CS301")), "CS301 is worth 3 credits.");
        assertThat(ask("How many credits is CS301?")).isEqualTo(CANNOT_VERIFY);
    }

    @Test
    void aFailureInALaterRoundStillFailsClosed() {
        when(toolExecutor.execute(any())).thenReturn(ok("get_my_course_section", section("CS301", "SEC-01", "11-402", "Tuesday 09:00-11:45", "Dr. John Smith")),
                failed("get_section_availability", "AI_TOOL_EXECUTION_FAILED"));
        model(tools(call("get_my_course_section", "courseIdentifier", "CS301")),
                tools(call("get_section_availability", "courseSectionId", 1501)), "Your section has 10 seats left.");
        assertThat(ask("Is my CS301 section full?")).isEqualTo(CANNOT_VERIFY);
        assertThat(sent(2)).hasSize(2);
    }

    @Test
    void invalidArgumentsGoBackToTheModelButAnUnverifiableAnswerIsReplaced() {
        when(toolExecutor.execute(any())).thenReturn(failed("get_section_availability", "INVALID_AI_TOOL_ARGUMENTS"));
        model(tools(call("get_section_availability", "courseSectionId", "abc")), "Section 1501 has 12 seats available.");
        assertThat(ask("Is section 1501 full?")).isEqualTo(
                com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl.StudentAssistantServiceImpl.UNGROUNDED_FALLBACK_MESSAGE);
        assertThat(sent(2)).hasSize(2);
    }

    @Test
    void theRecordedTimetableFallbackAlsoReportsAFailureHonestly() {
        when(toolExecutor.execute(any())).thenReturn(failed("get_my_schedule", "AI_TOOL_EXECUTION_FAILED"));
        assertThat(ask("What is my next class?")).endsWith(CANNOT_VERIFY).doesNotContain("No current active enrollments");
        verifyNoInteractions(llm);
    }

    @Test
    void providerFailuresKeepTheirExistingErrors() {
        when(llm.chat(any())).thenThrow(com.smartAiUniversityAssistant.seniorproject.feature.assistant.exception.AiAssistantFailure.unavailable());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ask("What courses am I enrolled in?"))
                .hasFieldOrPropertyWithValue("code", "AI_ASSISTANT_UNAVAILABLE");
        verifyNoInteractions(toolExecutor);
    }
}
