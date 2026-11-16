package com.smartAiUniversityAssistant.seniorproject.feature.campus.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.dto.CampusCoordinate;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.exception.CampusFailure;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** Shared write helpers: request text clean-up, coordinates, lock timeout, audit time and constraint translation. */
@Component
class CampusWriteSupport {
    private static final Pattern CODE = Pattern.compile("^[A-Z0-9][A-Z0-9._-]*$");
    private static final Pattern ROOM_NUMBER = Pattern.compile("^[A-Z0-9][A-Z0-9._ /-]*$");
    private static final Pattern REFERENCE_CODE = Pattern.compile("^[A-Z][A-Z0-9_]*$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9][0-9 ()-]{2,48}$");
    private static final Map<String, java.util.function.Supplier<CampusFailure>> CONSTRAINTS = Map.ofEntries(
            Map.entry("campus_sites_site_code_key", () -> CampusFailure.duplicate("The campus code")),
            Map.entry("campus_categories_category_code_key", () -> CampusFailure.duplicate("The category code")),
            Map.entry("campus_facilities_facility_code_key", () -> CampusFailure.duplicate("The facility code")),
            Map.entry("uk_campus_buildings_code", () -> CampusFailure.duplicate("The building code")),
            Map.entry("uk_campus_rooms_building_number", () -> CampusFailure.duplicate("The room number")),
            Map.entry("uk_campus_places_code", () -> CampusFailure.duplicate("The place code")),
            Map.entry("uk_campus_nav_node_code", () -> CampusFailure.duplicate("The node code")),
            Map.entry("uk_campus_nav_edge_code", () -> CampusFailure.duplicate("The edge code")),
            Map.entry("uk_campus_primary_building_destination", CampusFailure::primaryExists),
            Map.entry("uk_campus_primary_place_destination", CampusFailure::primaryExists),
            Map.entry("uk_campus_nav_destination_building_node", CampusFailure::duplicateDestination),
            Map.entry("uk_campus_nav_destination_place_node", CampusFailure::duplicateDestination));

    private final EntityManager entityManager;
    private final Clock clock;

    CampusWriteSupport(EntityManager entityManager, Clock clock) {
        this.entityManager = entityManager;
        this.clock = clock;
    }

    void lockTimeout() { entityManager.createNativeQuery("SET LOCAL lock_timeout = '3s'").executeUpdate(); }
    LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }

    String code(String value) {
        String code = value.trim().toUpperCase(Locale.ROOT);
        if (!CODE.matcher(code).matches()) throw CampusFailure.invalid();
        return code;
    }
    String roomNumber(String value) {
        String number = value.trim().toUpperCase(Locale.ROOT);
        if (!ROOM_NUMBER.matcher(number).matches()) throw CampusFailure.invalid();
        return number;
    }
    String referenceCode(String value) {
        String code = value.trim().toUpperCase(Locale.ROOT);
        if (!REFERENCE_CODE.matcher(code).matches()) throw CampusFailure.invalid();
        return code;
    }
    String required(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) throw CampusFailure.invalid();
        return text;
    }
    String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    String phone(String value) {
        String phone = optional(value);
        if (phone != null && !PHONE.matcher(phone).matches()) throw CampusFailure.invalid("The phone number format is invalid.");
        return phone;
    }
    /** Matches the NUMERIC(18,15) coordinate columns (V55). */
    static final int COORDINATE_SCALE = 15;
    /** Both values required, in range, and with no more precision than NUMERIC(18,15) stores. */
    void coordinates(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null || !inRange(latitude, longitude)
                || latitude.stripTrailingZeros().scale() > COORDINATE_SCALE || longitude.stripTrailingZeros().scale() > COORDINATE_SCALE)
            throw CampusFailure.invalidCoordinates();
    }
    void coordinates(CampusCoordinate point) {
        if (point == null) throw CampusFailure.invalidCoordinates();
        coordinates(point.latitude(), point.longitude());
    }
    static boolean inRange(BigDecimal latitude, BigDecimal longitude) {
        return latitude.abs().compareTo(BigDecimal.valueOf(90)) <= 0 && longitude.abs().compareTo(BigDecimal.valueOf(180)) <= 0;
    }
    List<Long> distinctIds(List<Long> ids) {
        if (ids == null) return List.of();
        if (new HashSet<>(ids).size() != ids.size()) throw CampusFailure.invalidFacility();
        return ids;
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
