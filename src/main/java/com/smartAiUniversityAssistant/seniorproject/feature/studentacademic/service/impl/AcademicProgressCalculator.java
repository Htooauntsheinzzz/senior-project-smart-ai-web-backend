package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.academicresult.enums.RepeatAttemptPolicy;
import java.math.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CGPA = SUM(gradePoint x credits) / SUM(credits) over attempts whose grade counts in GPA; earned credits add each
 * course once. Repeats follow the grading scheme's policy. When a Student has a repeated course and the policy is not
 * configured (or the attempts' schemes disagree), both metrics are unknown rather than guessed.
 */
final class AcademicProgressCalculator {
    /** order sorts a Student's attempts of one course from oldest to newest. */
    record Attempt(long courseId, long order, BigDecimal gradePoint, boolean countsInGpa, boolean earnsCredit,
            BigDecimal credits, RepeatAttemptPolicy policy) {}
    record Outcome(BigDecimal cgpa, BigDecimal creditsEarned) {}

    private static final Outcome UNKNOWN = new Outcome(null, null);

    private AcademicProgressCalculator() {}

    static Outcome calculate(List<Attempt> attempts) {
        if (attempts.isEmpty()) return UNKNOWN;
        var inGpa = new ArrayList<Attempt>();
        BigDecimal earned = BigDecimal.ZERO;
        Map<Long, List<Attempt>> byCourse = attempts.stream().collect(Collectors.groupingBy(Attempt::courseId));
        for (List<Attempt> group : byCourse.values()) {
            group.sort(Comparator.comparingLong(Attempt::order));
            RepeatAttemptPolicy policy = group.getFirst().policy();
            if (group.size() > 1 && (policy == null || group.stream().anyMatch(a -> a.policy() != policy))) return UNKNOWN;
            // A single attempt reads the same under every policy.
            List<Attempt> selected = group.size() == 1 ? group : select(group, policy);
            selected.stream().filter(Attempt::countsInGpa).forEach(inGpa::add);
            // Replacement semantics apply only to LATEST_ATTEMPT; otherwise any earning attempt earns the course once.
            List<Attempt> creditPool = policy == RepeatAttemptPolicy.LATEST_ATTEMPT ? selected : group;
            for (int i = creditPool.size() - 1; i >= 0; i--) {
                if (creditPool.get(i).earnsCredit()) {
                    earned = earned.add(creditPool.get(i).credits());
                    break;
                }
            }
        }
        return new Outcome(cgpa(inGpa), plain(earned));
    }

    private static List<Attempt> select(List<Attempt> group, RepeatAttemptPolicy policy) {
        return switch (policy) {
            case ALL_ATTEMPTS -> group;
            case LATEST_ATTEMPT -> List.of(group.getLast());
            case BEST_ATTEMPT -> {
                Attempt best = null;
                for (Attempt attempt : group) {
                    if (attempt.countsInGpa() && (best == null || attempt.gradePoint().compareTo(best.gradePoint()) >= 0))
                        best = attempt;
                }
                yield List.of(best == null ? group.getLast() : best);
            }
        };
    }

    private static BigDecimal cgpa(List<Attempt> attempts) {
        BigDecimal points = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        for (Attempt attempt : attempts) {
            points = points.add(attempt.gradePoint().multiply(attempt.credits()));
            credits = credits.add(attempt.credits());
        }
        return credits.signum() == 0 ? null : points.divide(credits, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal plain(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
