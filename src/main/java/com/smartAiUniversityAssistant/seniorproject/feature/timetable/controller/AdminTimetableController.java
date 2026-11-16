package com.smartAiUniversityAssistant.seniorproject.feature.timetable.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.service.TimetableService;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.service.impl.TimetableServiceImpl;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Admin timetable (spec file 42, section 27). Bodies and responses are DTOs; entities never leave the service. */
@RestController
@RequestMapping("/api/v1/admin/timetable")
public class AdminTimetableController {
    private final TimetableService timetable;
    public AdminTimetableController(TimetableService timetable) { this.timetable = timetable; }

    @GetMapping("/calendar") public ResponseEntity<TimetableCalendarResponse> calendar(HttpServletRequest request) {
        return ok(timetable.calendar(TimetablePageQuery.from(request, TimetableServiceImpl.CALENDAR_FILTERS, TimetableServiceImpl.ID_SORT, "id,asc")));
    }
    @GetMapping("/meetings") public ResponseEntity<TimetablePageResponse<TimetableMeetingListItem>> list(HttpServletRequest request) {
        return ok(timetable.list(TimetablePageQuery.from(request, TimetableServiceImpl.LIST_FILTERS, TimetableServiceImpl.LIST_SORT, "dayOfWeek,asc")));
    }
    @GetMapping("/meetings/{id:\\d+}") public ResponseEntity<TimetableMeetingResponse> detail(@PathVariable long id) { return ok(timetable.detail(id)); }
    @PostMapping("/meetings") public ResponseEntity<TimetableMeetingResponse> create(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody TimetableMeetingRequest body) {
        return created(timetable.create(actor, body));
    }
    @PostMapping("/meetings/bulk") public ResponseEntity<List<TimetableMeetingResponse>> createBulk(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody TimetableBulkMeetingRequest body) {
        return created(timetable.createBulk(actor, body));
    }
    @PutMapping("/meetings/{id:\\d+}") public ResponseEntity<TimetableMeetingResponse> update(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable long id, @Valid @RequestBody TimetableMeetingSlot body) {
        return ok(timetable.update(actor, id, body));
    }
    @DeleteMapping("/meetings/{id:\\d+}") public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        timetable.delete(actor, id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @PostMapping("/meetings/{id:\\d+}/publish") public ResponseEntity<TimetableMeetingResponse> publish(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        return ok(timetable.publish(actor, id));
    }
    @PostMapping("/meetings/{id:\\d+}/cancel") public ResponseEntity<TimetableMeetingResponse> cancel(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        return ok(timetable.cancel(actor, id));
    }
    @GetMapping("/conflicts") public ResponseEntity<TimetableConflictListResponse> conflicts(HttpServletRequest request) {
        return ok(timetable.conflicts(TimetablePageQuery.from(request, TimetableServiceImpl.CONFLICT_FILTERS, TimetableServiceImpl.ID_SORT, "id,asc")));
    }
    @PostMapping("/conflicts/check") public ResponseEntity<TimetableConflictCheckResponse> check(@Valid @RequestBody TimetableConflictCheckRequest body) {
        return ok(timetable.check(body));
    }
    @GetMapping("/statistics") public ResponseEntity<TimetableStatisticsResponse> statistics(@RequestParam(required = false) Long academicTermId) {
        return ok(timetable.statistics(academicTermId));
    }
    @GetMapping("/filter-options") public ResponseEntity<TimetableFilterOptionsResponse> filterOptions(@RequestParam(required = false) Long academicTermId,
            @RequestParam(required = false) Long facultyId, @RequestParam(required = false) Long departmentId, @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long campusId, @RequestParam(required = false) Long buildingId) {
        return ok(timetable.filterOptions(academicTermId, facultyId, departmentId, courseId, campusId, buildingId));
    }
    @GetMapping("/meetings/{id:\\d+}/exceptions") public ResponseEntity<List<TimetableExceptionResponse>> exceptions(@PathVariable long id) {
        return ok(timetable.exceptions(id));
    }
    @PostMapping("/meetings/{id:\\d+}/exceptions") public ResponseEntity<TimetableExceptionResponse> addException(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable long id, @Valid @RequestBody TimetableExceptionRequest body) {
        return created(timetable.addException(actor, id, body));
    }
    @PutMapping("/meetings/{id:\\d+}/exceptions/{exceptionId:\\d+}") public ResponseEntity<TimetableExceptionResponse> updateException(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id, @PathVariable long exceptionId, @Valid @RequestBody TimetableExceptionRequest body) {
        return ok(timetable.updateException(actor, id, exceptionId, body));
    }
    @DeleteMapping("/meetings/{id:\\d+}/exceptions/{exceptionId:\\d+}") public ResponseEntity<Void> deleteException(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable long id, @PathVariable long exceptionId) {
        timetable.deleteException(actor, id, exceptionId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    private static <T> ResponseEntity<T> ok(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
    private static <T> ResponseEntity<T> created(T body) { return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(body); }
}
