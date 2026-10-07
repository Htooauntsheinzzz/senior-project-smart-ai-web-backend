package com.smartAiUniversityAssistant.seniorproject.feature.enrollment.enums;

import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.exception.InvalidEnrollmentStatusException;
import java.util.*;

public enum EnrollmentStatus {
    PENDING,
    ACTIVE,
    WITHDRAWN,
    DROPPED;

    /** Statuses that occupy a seat in the course section. */
    public static final Set<EnrollmentStatus> SEAT_HOLDING = Collections.unmodifiableSet(EnumSet.of(PENDING, ACTIVE));

    public static EnrollmentStatus parse(String value) {
        if (value == null) throw new InvalidEnrollmentStatusException();
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidEnrollmentStatusException();
        }
    }

    public boolean holdsSeat() { return SEAT_HOLDING.contains(this); }

    // Same-state requests are idempotent; WITHDRAWN and DROPPED are terminal.
    public boolean canTransitionTo(EnrollmentStatus target) {
        if (this == target) return true;
        return switch (this) {
            case PENDING -> true;
            case ACTIVE -> target != PENDING;
            case WITHDRAWN, DROPPED -> false;
        };
    }
}
