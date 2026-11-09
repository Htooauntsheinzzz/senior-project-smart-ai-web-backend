package com.smartAiUniversityAssistant.seniorproject.feature.timetable.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.timetable.exception.TimetableFailure;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** Shared write helpers: lock timeout, audit time, search predicates and constraint translation. */
@Component
class TimetableWriteSupport {
    private static final Map<String, Supplier<TimetableFailure>> CONSTRAINTS = Map.of(
            "uk_academic_terms_semester_year", TimetableFailure::termDuplicate,
            "uk_timetable_exception_original_occurrence", TimetableFailure::exceptionDuplicate,
            "chk_published_meeting_room", () -> TimetableFailure.publishNotAllowed("A published meeting needs a campus room."),
            "fk_course_section_meetings_room", TimetableFailure::roomNotFound,
            "fk_course_sections_academic_term", TimetableFailure::termNotFound);

    private final EntityManager entityManager;
    private final Clock clock;

    TimetableWriteSupport(EntityManager entityManager, Clock clock) {
        this.entityManager = entityManager;
        this.clock = clock;
    }

    void lockTimeout() { entityManager.createNativeQuery("SET LOCAL lock_timeout = '3s'").executeUpdate(); }
    LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    Instant instant() { return clock.instant(); }

    static Predicate contains(CriteriaBuilder cb, Expression<String> field, String term) {
        String literal = term.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return cb.like(cb.lower(field), "%" + literal + "%", '\\');
    }

    /** A known constraint becomes its business error; anything else is rethrown unchanged. */
    RuntimeException translate(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation && violation.getConstraintName() != null) {
                var failure = CONSTRAINTS.get(violation.getConstraintName());
                if (failure != null) return failure.get();
            }
        }
        return exception;
    }
}
