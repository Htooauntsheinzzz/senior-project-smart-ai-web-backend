package com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding;

import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.AcademicEvidenceStatus.*;
import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.GroundingFixtures.*;
import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.KnowledgeEvidence.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.impl.GroundedAnswerValidatorImpl;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/** Spec file 53, section 20.4: passages as facts, three-digit credits and grade point averages with document evidence. */
class KnowledgeAnswerValidatorTests {
    private final GroundedAnswerValidator validator = new GroundedAnswerValidatorImpl();
    private static final AcademicEvidence ARTICLE3 = found("minimum credits per semester", regulation("Chapter 2 Registration", ARTICLE_3));
    private static final AcademicEvidence ARTICLE11 = found("graduation requirements", regulation("Chapter 5 Graduation", ARTICLE_11));

    private GroundingValidationResult check(String answer, AcademicEvidence... evidence) {
        return validator.validate(answer, List.of(evidence));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            You must register for at least 9 credits and at most 22 credits. | -
            You may register for up to 24 credits.                           | CREDIT_HOURS
            The rule is stated on pages 12-13.                               | -
            This is in the 2026 regulations.                                 | -
            This applies to the academic year 2026–2027.                     | -
            This changed in 2031.                                            | YEAR
            The office is open until 16:30.                                  | TIME
            Ask Dr. Somchai Jaidee.                                          | LECTURER
            You need 9-22 credits.                                           | ROOM
            """)
    void article3(String answer, String issue) {
        var result = check(answer, ARTICLE3);
        if ("-".equals(issue)) assertThat(result.valid()).as(result.issues().toString()).isTrue();
        else assertThat(result.issues()).containsExactly(issue);
    }

    @Test
    void namesAndCourseCodesOfTheQuery() {
        var withName = found("q", regulation(null, ARTICLE_3 + "\nContact Dr. Somchai Jaidee for exceptions."));
        assertThat(check("Ask Dr. Somchai Jaidee.", withName).valid()).isTrue();
        var query = found("is CS301 required for graduation", regulation(null, ARTICLE_11));
        assertThat(check("CS301 is not mentioned in the documents.", query).valid()).isTrue();
        assertThat(check("CS999 is required.", query).issues()).containsExactly("COURSE_CODE");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            You must complete at least 120 credits.                | -
            You must complete 130 credits.                         | CREDIT_HOURS
            The minimum GPA is 2.00.                               | -
            You need a GPA of at least 2.0.                        | -
            You need a cumulative grade point average of 2.50.     | GPA
            """)
    void article11(String answer, String issue) {
        var result = check(answer, ARTICLE11);
        if ("-".equals(issue)) assertThat(result.valid()).as(result.issues().toString()).isTrue();
        else assertThat(result.issues()).containsExactly(issue);
    }

    @Test
    void wordsOrdinalsAndGpaForms() {
        var words = found("q", regulation(null, "A student registers for nine credits in the first semester."));
        assertThat(check("You register for 9 credits in semester 1.", words).valid()).isTrue();
        var gpa = found("q", regulation(null, "Honours need a GPA of 3.50."));
        assertThat(check("You need a 3.5 GPA.", gpa).valid()).isTrue();
        assertThat(check("A GPA of 2.0 is enough.", ARTICLE11).valid()).isTrue();
        assertThat(check("A GPA of 2.00 is enough.", ARTICLE11).valid()).isTrue();
        assertThat(check("A 3.5 GPA is enough.", ARTICLE11).issues()).containsExactly("GPA");
        // The digits of a decimal number are not whole numbers: "2.00" does not support "2 credits" or "00".
        assertThat(check("You need 2 credits.", found("q", regulation(null, "A grade point average of 2.00.")))
                .issues()).containsExactly("CREDIT_HOURS");
    }

    @Test
    void onlyDatabaseEvidenceIsCheckedAsInStep11() {
        var courses = new AcademicEvidence("get_my_courses", VERIFIED, List.of(enrollment("CS301", "Data Structures", "SEC-01")),
                java.time.Instant.EPOCH, null, null);
        assertThat(check("You need 120 credits and a GPA of 2.00.", courses).valid()).isTrue();
        for (AcademicEvidence notVerified : List.of(empty(), unavailable(), error()))
            assertThat(check("You need 120 credits and a GPA of 2.00.", courses, notVerified).valid()).isTrue();
        // A failed or empty search adds no fact: a passage value stays unsupported.
        assertThat(check("Your CS301 section has 22 credits.", courses, empty()).issues()).containsExactly("CREDIT_HOURS");
    }
}
