package com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding;

import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.AcademicEvidenceStatus.*;
import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.KnowledgeEvidence.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.impl.GroundedAnswerValidatorImpl;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.impl.SafeAcademicResponseFactoryImpl;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Spec file 57, sections 18.3 and 18.4: CREDITS_MORE, derived credit values and degreeAuditReferral(). */
class HybridAnswerValidatorTests {
    private final GroundedAnswerValidator validator = new GroundedAnswerValidatorImpl();
    static final String C0 = "Section 1 Program Overview\nA student must complete at least 132 credits to graduate from this program.";
    static final String C5 = "To register for CSC490 a student must have earned at least 90 credits and must have a cumulative grade point "
            + "average of at least 2.00.";
    static final AcademicEvidence PASSAGES = found("q", regulation("Chapter 5 Graduation", ARTICLE_11),
            passage("Sample B.Sc. Computer Science Curriculum 2026", "2026-2027", "Section 1 Program Overview", null, C0),
            passage("Sample B.Sc. Computer Science Curriculum 2026", "2026-2027", "Section 5", null, C5));
    static final AcademicEvidence A = ProgressGroundingTests.A;

    private GroundingValidationResult check(String answer, AcademicEvidence... evidence) {
        return validator.validate(answer, List.of(evidence));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            The curriculum requires at least 132 credits. You have earned 18 credits, so at least 114 more credits remain.              | -
            It requires at least 132 credits. If you pass the 9 credits you are taking now, at least 105 more credits will remain.   | -
            If you pass the 9 credits you are taking now, you will then have 27 credits.                                             | -
            It requires at least 132 credits, so at least 104 more credits remain.                                                   | CREDIT_HOURS
            You need 114 more credits.                                                                                               | CREDIT_HOURS
            You have earned 21 credits.                                                                                              | CREDIT_HOURS
            Your cumulative GPA is 2.75.                                                                                             | GPA
            You need 132-18 credits.                                                                                                 | ROOM
            You have met all graduation requirements.                                                                                | STANDING
            The university requires at least 120 credits; you have earned 18, so at least 102 more credits remain.                   | -
            """)
    void table9_3(String answer, String issue) {
        var result = check(answer, A, PASSAGES);
        if ("-".equals(issue)) assertThat(result.valid()).as(result.issues().toString()).isTrue();
        else assertThat(result.issues()).contains(issue);
    }

    @Test
    void creditsMore() {
        var evidence = found("q", regulation(null, "Students may need 3 or 6 more credits."));
        assertThat(check("You need 114 more credits.", evidence).issues()).containsExactly("CREDIT_HOURS");
        assertThat(check("You need 3 additional credits.", evidence).valid()).isTrue();
        assertThat(check("You need 6 remaining credits.", evidence).valid()).isTrue();
        assertThat(check("You need 7 additional credits.", evidence).issues()).containsExactly("CREDIT_HOURS");
        assertThat(check("You need 3 credits more.", evidence).valid()).isTrue();
    }

    @Test
    void noDerivationWithoutBothKindsOfEvidence() {
        String answer = "It requires at least 132 credits. You have earned 18 credits, so at least 114 more credits remain.";
        assertThat(check(answer, A).issues()).contains("CREDIT_HOURS");
        assertThat(check(answer, PASSAGES).issues()).contains("CREDIT_HOURS");
        var nullEarned = new AcademicEvidence("get_my_academic_progress", VERIFIED, ProgressGroundingTests.progress("2.57", null, 3, 9),
                Instant.EPOCH, null, null);
        assertThat(check("It requires at least 132 credits, so at least 114 more credits remain.", nullEarned, PASSAGES).issues())
                .contains("CREDIT_HOURS");
        var half = new AcademicEvidence("get_my_academic_progress", VERIFIED, ProgressGroundingTests.progress("2.57", "18.5", 3, 9),
                Instant.EPOCH, null, null);
        assertThat(check("It requires at least 132 credits, so at least 114 more credits remain.", half, PASSAGES).issues())
                .contains("CREDIT_HOURS");
        var noCurrent = new AcademicEvidence("get_my_academic_progress", VERIFIED, ProgressGroundingTests.progress("2.57", "18", 3, null),
                Instant.EPOCH, null, null);
        assertThat(check("It requires at least 132 credits, so at least 114 more credits remain.", noCurrent, PASSAGES).valid()).isTrue();
        assertThat(check("It requires at least 132 credits, so at least 105 more credits remain.", noCurrent, PASSAGES).issues())
                .contains("CREDIT_HOURS");
        assertThat(check("You will then have 27 credits.", noCurrent, PASSAGES).issues()).contains("CREDIT_HOURS");
    }

    @Test
    void degreeAuditReferral() {
        assertThat(new SafeAcademicResponseFactoryImpl().degreeAuditReferral()).isEqualTo("I could not reliably compare your records with "
                + "these requirements. Please compare the values above yourself, or ask the registrar's office for an official degree audit.");
    }
}
