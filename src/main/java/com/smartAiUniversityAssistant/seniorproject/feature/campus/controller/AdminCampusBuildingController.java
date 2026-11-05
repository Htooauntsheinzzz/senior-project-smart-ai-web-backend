package com.smartAiUniversityAssistant.seniorproject.feature.campus.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.service.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/campus/buildings")
public class AdminCampusBuildingController {
    private static final Set<String> SORT = Set.of("id", "buildingCode", "buildingName", "totalFloors", "status", "createdAt");
    private final CampusBuildingService buildings;
    public AdminCampusBuildingController(CampusBuildingService buildings) { this.buildings = buildings; }

    @GetMapping public ResponseEntity<CampusPageResponse<CampusBuildingListResponse>> list(HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(buildings.list(CampusPageQuery.from(request, Set.of("siteId", "status"), SORT, "buildingName,asc")));
    }
    @GetMapping("/statistics") public ResponseEntity<CampusBuildingStatisticsResponse> statistics(@RequestParam(required = false) Long siteId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(buildings.statistics(siteId));
    }
    @GetMapping("/{id}") public ResponseEntity<CampusBuildingResponse> detail(@PathVariable long id) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(buildings.detail(id)); }
    @GetMapping("/{id}/rooms") public ResponseEntity<List<CampusRoomResponse>> rooms(@PathVariable long id) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(buildings.rooms(id)); }
    @PostMapping public ResponseEntity<CampusBuildingResponse> create(@AuthenticationPrincipal AuthenticatedUser actor, @Valid @RequestBody CampusBuildingCreateRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(buildings.create(actor, body));
    }
    @PutMapping("/{id}") public ResponseEntity<CampusBuildingResponse> update(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id, @Valid @RequestBody CampusBuildingUpdateRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(buildings.update(actor, id, body));
    }
    @PatchMapping("/{id}/status") public ResponseEntity<CampusBuildingResponse> status(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id, @Valid @RequestBody CampusBuildingStatusRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(buildings.status(actor, id, body));
    }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        buildings.delete(actor, id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @PutMapping("/{id}/opening-hours") public ResponseEntity<List<CampusOpeningHourDto>> openingHours(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id, @Valid @RequestBody CampusOpeningHoursRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(buildings.openingHours(actor, id, body));
    }
    @PutMapping("/{id}/opening-exceptions") public ResponseEntity<List<CampusOpeningExceptionDto>> openingExceptions(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id, @Valid @RequestBody CampusOpeningExceptionsRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(buildings.openingExceptions(actor, id, body));
    }
}
