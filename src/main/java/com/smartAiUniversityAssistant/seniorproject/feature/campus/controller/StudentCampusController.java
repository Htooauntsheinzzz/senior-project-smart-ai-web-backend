package com.smartAiUniversityAssistant.seniorproject.feature.campus.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.service.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Read-only published campus data. Authentication and the active-account check are enforced in SecurityConfig and the JWT converter. */
@RestController
@RequestMapping("/api/v1/student/campus")
public class StudentCampusController {
    private final StudentCampusService campus;
    private final CampusRoutingService routing;
    public StudentCampusController(StudentCampusService campus, CampusRoutingService routing) {
        this.campus = campus;
        this.routing = routing;
    }

    @GetMapping("/sites") public ResponseEntity<List<StudentCampusSiteResponse>> sites() { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(campus.sites()); }
    @GetMapping("/categories") public ResponseEntity<List<StudentCampusCategoryResponse>> categories() { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(campus.categories()); }
    @GetMapping("/locations") public ResponseEntity<StudentCampusLocationsResponse> locations(@RequestParam(required = false) Long siteId,
            @RequestParam(required = false) String search, @RequestParam(required = false) String group,
            @RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(campus.locations(siteId, search, group, limit, cursor));
    }
    @GetMapping("/places/{id}") public ResponseEntity<StudentCampusPlaceResponse> place(@PathVariable long id) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(campus.place(id)); }
    @GetMapping("/buildings/{id}") public ResponseEntity<StudentCampusBuildingResponse> building(@PathVariable long id) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(campus.building(id)); }
    @GetMapping("/buildings/{id}/rooms") public ResponseEntity<StudentCampusRoomsResponse> rooms(@PathVariable long id) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(campus.buildingRooms(id)); }
    @GetMapping("/rooms/{id}") public ResponseEntity<StudentCampusRoomResponse> room(@PathVariable long id) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(campus.room(id)); }
    @GetMapping("/map") public ResponseEntity<StudentCampusMapResponse> map(@RequestParam(required = false) Long siteId,
            @RequestParam(defaultValue = "false") boolean includeNavigationGraph) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(campus.map(siteId, includeNavigationGraph));
    }
    @PostMapping("/nearby") public ResponseEntity<CampusNearbyResponse> nearby(@Valid @RequestBody CampusNearbyRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(routing.nearby(request));
    }
    @PostMapping("/routes/preview") public ResponseEntity<CampusRouteResponse> preview(@Valid @RequestBody CampusRoutePreviewRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(routing.preview(request));
    }
}
