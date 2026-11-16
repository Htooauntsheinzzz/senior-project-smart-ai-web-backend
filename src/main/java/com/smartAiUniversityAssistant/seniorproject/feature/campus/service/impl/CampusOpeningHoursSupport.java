package com.smartAiUniversityAssistant.seniorproject.feature.campus.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.entity.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.exception.CampusFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.mapper.CampusMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.repository.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** Weekly hours and exceptions for an owner that is either a building or a place. Runs inside the caller's transaction. */
@Component
class CampusOpeningHoursSupport {
    private final CampusOpeningHourRepository hours;
    private final CampusOpeningExceptionRepository exceptions;
    private final CampusMapper mapper;

    CampusOpeningHoursSupport(CampusOpeningHourRepository hours, CampusOpeningExceptionRepository exceptions, CampusMapper mapper) {
        this.hours = hours;
        this.exceptions = exceptions;
        this.mapper = mapper;
    }

    List<CampusOpeningHourDto> replaceHours(Long buildingId, Long placeId, CampusOpeningHoursRequest request) {
        var seen = new HashSet<Integer>();
        for (var row : request.hours()) {
            if (!seen.add(row.dayOfWeek())) throw CampusFailure.invalidOpeningHours("A day may appear only once.");
            times(row.closed(), row.opensAt(), row.closesAt());
        }
        if (buildingId != null) hours.removeForBuilding(buildingId); else hours.removeForPlace(placeId);
        var rows = request.hours().stream().sorted(Comparator.comparing(CampusOpeningHourDto::dayOfWeek)).map(row -> {
            var hour = new CampusOpeningHour();
            hour.setBuildingId(buildingId);
            hour.setPlaceId(placeId);
            hour.setDayOfWeek(row.dayOfWeek().shortValue());
            hour.setClosed(row.closed());
            hour.setOpensAt(row.opensAt());
            hour.setClosesAt(row.closesAt());
            return hour;
        }).toList();
        return hours.saveAllAndFlush(rows).stream().map(mapper::hour).toList();
    }

    List<CampusOpeningExceptionDto> replaceExceptions(Long buildingId, Long placeId, CampusOpeningExceptionsRequest request) {
        var seen = new HashSet<LocalDate>();
        for (var row : request.exceptions()) {
            if (!seen.add(row.date())) throw CampusFailure.invalidOpeningHours("A date may appear only once.");
            times(row.closed(), row.opensAt(), row.closesAt());
        }
        if (buildingId != null) exceptions.removeForBuilding(buildingId); else exceptions.removeForPlace(placeId);
        var rows = request.exceptions().stream().sorted(Comparator.comparing(CampusOpeningExceptionDto::date)).map(row -> {
            var exception = new CampusOpeningException();
            exception.setBuildingId(buildingId);
            exception.setPlaceId(placeId);
            exception.setExceptionDate(row.date());
            exception.setClosed(row.closed());
            exception.setOpensAt(row.opensAt());
            exception.setClosesAt(row.closesAt());
            exception.setReason(row.reason() == null || row.reason().isBlank() ? null : row.reason().trim());
            return exception;
        }).toList();
        return exceptions.saveAllAndFlush(rows).stream().map(mapper::exception).toList();
    }

    /** Overnight hours are rejected: no cross-day policy exists yet (spec section 14). */
    private void times(boolean closed, LocalTime opens, LocalTime closes) {
        if (closed) {
            if (opens != null || closes != null) throw CampusFailure.invalidOpeningHours("A closed day cannot have opening times.");
            return;
        }
        if (opens == null || closes == null) throw CampusFailure.invalidOpeningHours("An open day needs both opening and closing times.");
        if (!closes.isAfter(opens)) throw CampusFailure.invalidOpeningHours("The closing time must be after the opening time. Overnight hours are not supported.");
    }

    Map<Long, List<CampusOpeningHour>> hoursForBuildings(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        var grouped = new HashMap<Long, List<CampusOpeningHour>>();
        for (var hour : hours.findByBuildingIdInOrderByDayOfWeekAsc(ids)) grouped.computeIfAbsent(hour.getBuildingId(), k -> new ArrayList<>()).add(hour);
        return grouped;
    }
    Map<Long, List<CampusOpeningHour>> hoursForPlaces(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        var grouped = new HashMap<Long, List<CampusOpeningHour>>();
        for (var hour : hours.findByPlaceIdInOrderByDayOfWeekAsc(ids)) grouped.computeIfAbsent(hour.getPlaceId(), k -> new ArrayList<>()).add(hour);
        return grouped;
    }
    List<CampusOpeningHour> buildingHours(long id) { return hours.findByBuildingIdOrderByDayOfWeekAsc(id); }
    List<CampusOpeningHour> placeHours(long id) { return hours.findByPlaceIdOrderByDayOfWeekAsc(id); }
    List<CampusOpeningException> buildingExceptions(long id) { return exceptions.findByBuildingIdOrderByExceptionDateAsc(id); }
    List<CampusOpeningException> placeExceptions(long id) { return exceptions.findByPlaceIdOrderByExceptionDateAsc(id); }
    Map<Long, Map<LocalDate, CampusOpeningException>> exceptionsForPlaces(Collection<Long> ids, Collection<LocalDate> dates) {
        if (ids.isEmpty() || dates.isEmpty()) return Map.of();
        var grouped = new HashMap<Long, Map<LocalDate, CampusOpeningException>>();
        for (var row : exceptions.findByPlaceIdInAndExceptionDateIn(ids, dates))
            grouped.computeIfAbsent(row.getPlaceId(), k -> new HashMap<>()).put(row.getExceptionDate(), row);
        return grouped;
    }
    List<CampusOpeningHourDto> dtos(List<CampusOpeningHour> rows) { return rows.stream().map(mapper::hour).toList(); }
    List<CampusOpeningExceptionDto> exceptionDtos(List<CampusOpeningException> rows) { return rows.stream().map(mapper::exception).toList(); }
}
