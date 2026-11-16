package com.smartAiUniversityAssistant.seniorproject.feature.assistant;

import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AcademicAiToolNames.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.ResolvedAcademicReference.Kind;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl.AcademicReferenceResolverImpl;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Step 10 reference validation without Spring, Redis or a model. */
class AcademicReferenceResolverTests {
    private final AcademicReferenceResolverImpl resolver = new AcademicReferenceResolverImpl();
    private static final List<PresentedCourseReference> SHOWN = List.of(
            new PresentedCourseReference(1, "CS301", 1501L), new PresentedCourseReference(2, "CSC452", 1503L));

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Who teaches the first one?|ORDINAL|CS301", "Where is the second one?|ORDINAL|CSC452",
            "who teaches the 2nd course|ORDINAL|CSC452", "Is the last one full?|ORDINAL|CSC452",
            "And the first?|ORDINAL|CS301", "Tell me about number 2|ORDINAL|CSC452", "What about #1?|ORDINAL|CS301",
            "Who teaches the third one?|OUT_OF_RANGE|", "Where is it?|ACTIVE|CS301", "Who teaches that course?|ACTIVE|CS301",
            "Now who teaches CSC452?|EXPLICIT|", "Is section 1501 full?|EXPLICIT|", "Thanks!|NONE|",
            "What is my first semester GPA?|NONE|", "Is section number 2 full?|EXPLICIT|"})
    void resolvesFollowUpsAgainstTheConversation(String message, Kind kind, String course) {
        var resolved = resolver.resolve(message, conversation(new AcademicConversationReference("CS301", 1501L, GET_MY_COURSE_LECTURER, SHOWN)));
        assertThat(resolved.kind()).isEqualTo(kind);
        assertThat(resolved.courseCode()).isEqualTo(course);
    }

    @Test
    void nothingResolvesInANewConversationOrWithoutAClearReferent() {
        var fresh = resolver.resolve("Where is it?", null);
        assertThat(fresh.kind()).isEqualTo(Kind.NONE);
        assertThat(resolver.promptContext(fresh, null)).isEmpty();

        var noActive = conversation(new AcademicConversationReference(null, null, GET_MY_COURSES, SHOWN));
        var it = resolver.resolve("Where is it?", noActive);
        assertThat(it.kind()).isEqualTo(Kind.AMBIGUOUS);
        assertThat(resolver.promptContext(it, noActive)).contains("Do not guess");
        assertThat(resolver.requiresAcademicEvidence(it, "Where is it?")).isFalse();

        var noList = conversation(AcademicConversationReference.EMPTY);
        var first = resolver.resolve("Who teaches the first one?", noList);
        assertThat(first.kind()).isEqualTo(Kind.OUT_OF_RANGE);
        assertThat(resolver.promptContext(first, noList)).contains("no course list has been shown").contains("Do not guess");
        assertThat(resolver.resolve("Is the last one full?", noList).kind()).isEqualTo(Kind.OUT_OF_RANGE);
    }

    @Test
    void aClearFollowUpQuestionMustBeAnsweredFromAFreshToolResult() {
        var context = conversation(new AcademicConversationReference("CS301", 1501L, GET_MY_COURSE_LECTURER, SHOWN));
        var where = resolver.resolve("Where is it?", context);
        assertThat(resolver.requiresAcademicEvidence(where, "Where is it?")).isTrue();
        assertThat(resolver.promptContext(where, context)).contains("1. CS301 (courseSectionId 1501), 2. CSC452 (courseSectionId 1503).")
                .contains("Course currently being discussed: CS301.").contains("refers to CS301.");
        // A remark that merely mentions "it" is not a question that needs records.
        assertThat(resolver.requiresAcademicEvidence(resolver.resolve("Thanks, I like it", context), "Thanks, I like it")).isFalse();
    }

    @Test
    void presentedOrderFollowsTheAnswerNotTheToolOrder() {
        var rows = List.of(row("CS301", 1501), row("CSC452", 1503), row("CSC460", 1504));
        var next = resolver.nextReference(AcademicConversationReference.EMPTY, List.of(round(GET_MY_COURSES, rows)),
                "You have:\n1. CSC460 Cloud\n2. CS301 Data Structures\n3. CSC452 Mobile (also see CS301)");
        assertThat(next.lastPresentedCourses()).extracting(PresentedCourseReference::courseCode).containsExactly("CSC460", "CS301", "CSC452");
        assertThat(next.lastPresentedCourses()).extracting(PresentedCourseReference::displayIndex).containsExactly(1, 2, 3);
        assertThat(next.activeCourseCode()).isNull();
        assertThat(next.lastAcademicIntent()).isEqualTo(GET_MY_COURSES);

        // Courses the answer never showed are not remembered; "CS3010" is not CS301.
        var partial = resolver.nextReference(AcademicConversationReference.EMPTY, List.of(round(GET_MY_COURSES, rows)), "Only CSC452 and CS3010.");
        assertThat(partial.lastPresentedCourses()).extracting(PresentedCourseReference::courseCode).containsExactly("CSC452");
        assertThat(partial.activeCourseCode()).isEqualTo("CSC452");
        assertThat(resolver.nextReference(AcademicConversationReference.EMPTY, List.of(round(GET_MY_COURSES, rows)), "You have three courses.")
                .lastPresentedCourses()).isEmpty();
    }

    @Test
    void activeCourseFollowsSuccessfulSingleCourseResultsOnly() {
        var previous = new AcademicConversationReference("CS301", 1501L, GET_MY_COURSE_LECTURER, SHOWN);
        var lecturer = round(GET_MY_COURSE_LECTURER, row("CSC452", 1503));
        var switched = resolver.nextReference(previous, List.of(lecturer), "CSC452 is taught by Dr. Jane Lee.");
        assertThat(switched.activeCourseCode()).isEqualTo("CSC452");
        assertThat(switched.activeCourseSectionId()).isEqualTo(1503L);
        assertThat(switched.lastPresentedCourses()).isEqualTo(SHOWN);

        // Two different courses in one answer: no single active course.
        var two = resolver.nextReference(previous, List.of(lecturer, round(GET_COURSE_INFORMATION, row("CS301", null))), "...");
        assertThat(two.activeCourseCode()).isNull();

        // A failed tool or a turn without tools keeps the earlier pointer.
        var failed = new LlmToolRound(new LlmResponse(null, "m", null), List.of(new LlmToolResult("c", GET_MY_COURSE_SECTION,
                Map.of("success", false, "code", "ACADEMIC_COURSE_NOT_ENROLLED"))));
        assertThat(resolver.nextReference(previous, List.of(failed), "You are not enrolled.").activeCourseCode()).isEqualTo("CS301");
        assertThat(resolver.nextReference(previous, List.of(), "A credit hour is ...")).isEqualTo(previous);
    }

    private static AssistantConversationContext conversation(AcademicConversationReference reference) {
        var now = Instant.EPOCH;
        return new AssistantConversationContext(1, UUID.randomUUID().toString(), 15L, now, now, 1,
                List.of(ConversationMessage.user("q", now), ConversationMessage.assistant("a", now)), reference);
    }

    private static Map<String, Object> row(String code, Integer section) {
        var row = new LinkedHashMap<String, Object>();
        row.put("courseCode", code);
        row.put("courseSectionId", section == null ? null : section.longValue());
        return row;
    }

    private static LlmToolRound round(String tool, Object data) {
        return new LlmToolRound(new LlmResponse(null, "m", null), List.of(new LlmToolResult("c", tool, Map.of("success", true, "data", data))));
    }
}
