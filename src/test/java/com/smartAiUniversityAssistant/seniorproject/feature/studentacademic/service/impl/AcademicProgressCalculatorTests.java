package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartAiUniversityAssistant.seniorproject.feature.academicresult.enums.RepeatAttemptPolicy;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service.impl.AcademicProgressCalculator.Attempt;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class AcademicProgressCalculatorTests {
    private static final RepeatAttemptPolicy NONE = null;

    private static Attempt graded(long course, long order, String point, String credits, boolean earns, RepeatAttemptPolicy policy) {
        return new Attempt(course, order, new BigDecimal(point), true, earns, new BigDecimal(credits), policy);
    }
    private static Attempt ungraded(long course, long order, String credits, boolean earns) {
        return new Attempt(course, order, null, false, earns, new BigDecimal(credits), NONE);
    }
    private static AcademicProgressCalculator.Outcome calc(Attempt... attempts) {
        return AcademicProgressCalculator.calculate(new ArrayList<>(List.of(attempts)));
    }

    @Test void noResultsAreUnknownNotZero() {
        var outcome = calc();
        assertThat(outcome.cgpa()).isNull();
        assertThat(outcome.creditsEarned()).isNull();
    }

    @Test void weightsByCredits() {
        var outcome = calc(graded(1, 0, "4.00", "3.00", true, NONE), graded(2, 1, "2.00", "1.00", true, NONE));
        assertThat(outcome.cgpa()).isEqualByComparingTo("3.50");
        assertThat(outcome.creditsEarned()).isEqualByComparingTo("4");
        assertThat(outcome.creditsEarned().toString()).isEqualTo("4");
    }

    @Test void largeRoundCreditTotalStaysPlain() {
        var outcome = calc(graded(1, 0, "4.00", "90.00", true, NONE));
        assertThat(outcome.creditsEarned().toString()).isEqualTo("90");
    }

    @Test void roundsHalfUpToTwoPlaces() {
        // (4*2 + 3*1) / 3 = 3.6666...
        assertThat(calc(graded(1, 0, "4.00", "2.00", true, NONE), graded(2, 1, "3.00", "1.00", true, NONE)).cgpa())
                .isEqualByComparingTo("3.67");
    }

    @Test void failCountsInGpaButEarnsNothing() {
        var outcome = calc(graded(1, 0, "4.00", "3.00", true, NONE), graded(2, 1, "0.00", "3.00", false, NONE));
        assertThat(outcome.cgpa()).isEqualByComparingTo("2.00");
        assertThat(outcome.creditsEarned()).isEqualByComparingTo("3");
    }

    @Test void passFailEarnsCreditOutsideGpa() {
        var outcome = calc(graded(1, 0, "3.00", "3.00", true, NONE), ungraded(2, 1, "2.00", true));
        assertThat(outcome.cgpa()).isEqualByComparingTo("3.00");
        assertThat(outcome.creditsEarned()).isEqualByComparingTo("5");
    }

    @Test void resultsOutsideGpaLeaveCgpaUnknownButCreditsReal() {
        var outcome = calc(ungraded(1, 0, "2.00", true));
        assertThat(outcome.cgpa()).isNull();
        assertThat(outcome.creditsEarned()).isEqualByComparingTo("2");
    }

    @Test void onlyFailedResultsEarnARealZero() {
        var outcome = calc(graded(1, 0, "0.00", "3.00", false, NONE));
        assertThat(outcome.cgpa()).isEqualByComparingTo("0.00");
        assertThat(outcome.creditsEarned()).isEqualByComparingTo("0");
    }

    @Test void repeatWithoutConfiguredPolicyIsUnknown() {
        var outcome = calc(graded(1, 0, "0.00", "3.00", false, NONE), graded(1, 1, "3.00", "3.00", true, NONE),
                graded(2, 2, "4.00", "3.00", true, NONE));
        assertThat(outcome.cgpa()).isNull();
        assertThat(outcome.creditsEarned()).isNull();
    }

    @Test void repeatWithDisagreeingSchemesIsUnknown() {
        var outcome = calc(graded(1, 0, "0.00", "3.00", false, RepeatAttemptPolicy.LATEST_ATTEMPT),
                graded(1, 1, "3.00", "3.00", true, RepeatAttemptPolicy.BEST_ATTEMPT));
        assertThat(outcome.cgpa()).isNull();
    }

    @Test void latestAttemptReplacesEarlierOnes() {
        var p = RepeatAttemptPolicy.LATEST_ATTEMPT;
        var outcome = calc(graded(1, 0, "4.00", "3.00", true, p), graded(1, 1, "2.00", "3.00", true, p));
        assertThat(outcome.cgpa()).isEqualByComparingTo("2.00");
        assertThat(outcome.creditsEarned()).isEqualByComparingTo("3");
        var failedLater = calc(graded(1, 0, "4.00", "3.00", true, p), graded(1, 1, "0.00", "3.00", false, p));
        assertThat(failedLater.cgpa()).isEqualByComparingTo("0.00");
        assertThat(failedLater.creditsEarned()).isEqualByComparingTo("0");
    }

    @Test void bestAttemptKeepsTheHighestGrade() {
        var p = RepeatAttemptPolicy.BEST_ATTEMPT;
        var outcome = calc(graded(1, 0, "3.50", "3.00", true, p), graded(1, 1, "2.00", "3.00", true, p));
        assertThat(outcome.cgpa()).isEqualByComparingTo("3.50");
        assertThat(outcome.creditsEarned()).isEqualByComparingTo("3");
    }

    @Test void allAttemptsAverageButCreditsCountOnce() {
        var p = RepeatAttemptPolicy.ALL_ATTEMPTS;
        var outcome = calc(graded(1, 0, "1.00", "3.00", true, p), graded(1, 1, "3.00", "3.00", true, p));
        assertThat(outcome.cgpa()).isEqualByComparingTo("2.00");
        assertThat(outcome.creditsEarned()).isEqualByComparingTo("3");
    }
}
