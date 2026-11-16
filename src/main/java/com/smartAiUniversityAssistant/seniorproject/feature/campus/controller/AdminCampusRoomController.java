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
@RequestMapping("/api/v1/admin/campus/rooms")
public class AdminCampusRoomController {
    private static final Set<String> SORT = Set.of("id", "roomNumber", "roomName", "floorNumber", "roomType", "capacity", "status", "createdAt");
    private final CampusRoomService rooms;
    private final CampusCatalogService catalog;
    public AdminCampusRoomController(CampusRoomService rooms, CampusCatalogService catalog) {
        this.rooms = rooms;
        this.catalog = catalog;
    }

    @GetMapping public ResponseEntity<CampusPageResponse<CampusRoomResponse>> list(HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(rooms.list(CampusPageQuery.from(request, Set.of("buildingId", "floorNumber", "roomType", "status"), SORT, "roomNumber,asc")));
    }
    @GetMapping("/statistics") public ResponseEntity<CampusRoomStatisticsResponse> statistics(@RequestParam(required = false) Long siteId, @RequestParam(required = false) Long buildingId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(rooms.statistics(siteId, buildingId));
    }
    @GetMapping("/facilities") public ResponseEntity<List<CampusFacilityResponse>> facilities() { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(catalog.facilities(true)); }
    @GetMapping("/{id}") public ResponseEntity<CampusRoomResponse> detail(@PathVariable long id) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(rooms.detail(id)); }
    @PostMapping public ResponseEntity<CampusRoomResponse> create(@AuthenticationPrincipal AuthenticatedUser actor, @Valid @RequestBody CampusRoomRequest body) { return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(rooms.create(actor, body)); }
    @PutMapping("/{id}") public ResponseEntity<CampusRoomResponse> update(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id, @Valid @RequestBody CampusRoomRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(rooms.update(actor, id, body));
    }
    @PatchMapping("/{id}/status") public ResponseEntity<CampusRoomResponse> status(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id, @Valid @RequestBody CampusRoomStatusRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(rooms.status(actor, id, body));
    }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        rooms.delete(actor, id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
