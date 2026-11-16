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
@RequestMapping("/api/v1/admin/campus/places")
public class AdminCampusPlaceController {
    private static final Set<String> SORT = Set.of("id", "placeCode", "placeName", "status", "createdAt");
    private final CampusPlaceService places;
    public AdminCampusPlaceController(CampusPlaceService places) { this.places = places; }

    @GetMapping public ResponseEntity<CampusPageResponse<CampusPlaceResponse>> list(HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(places.list(CampusPageQuery.from(request, Set.of("siteId", "categoryId", "buildingId", "status", "mobileGroup", "isPopular", "isVisibleOnMobile"), SORT, "placeName,asc")));
    }
    @GetMapping("/statistics") public ResponseEntity<CampusPlaceStatisticsResponse> statistics(@RequestParam(required = false) Long siteId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(places.statistics(siteId));
    }
    @GetMapping("/{id}") public ResponseEntity<CampusPlaceResponse> detail(@PathVariable long id) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(places.detail(id)); }
    @PostMapping public ResponseEntity<CampusPlaceResponse> create(@AuthenticationPrincipal AuthenticatedUser actor, @Valid @RequestBody CampusPlaceCreateRequest body) { return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(places.create(actor, body)); }
    @PutMapping("/{id}") public ResponseEntity<CampusPlaceResponse> update(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id, @Valid @RequestBody CampusPlaceUpdateRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(places.update(actor, id, body));
    }
    @PatchMapping("/{id}/status") public ResponseEntity<CampusPlaceResponse> status(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id, @Valid @RequestBody CampusPlaceStatusRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(places.status(actor, id, body));
    }
    @PatchMapping("/{id}/visibility") public ResponseEntity<CampusPlaceResponse> visibility(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id, @Valid @RequestBody CampusPlaceVisibilityRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(places.visibility(actor, id, body));
    }
    @PatchMapping("/{id}/popular") public ResponseEntity<CampusPlaceResponse> popular(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id, @Valid @RequestBody CampusPlacePopularRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(places.popular(actor, id, body));
    }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        places.delete(actor, id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @PutMapping("/{id}/opening-hours") public ResponseEntity<List<CampusOpeningHourDto>> openingHours(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id, @Valid @RequestBody CampusOpeningHoursRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(places.openingHours(actor, id, body));
    }
    @PutMapping("/{id}/opening-exceptions") public ResponseEntity<List<CampusOpeningExceptionDto>> openingExceptions(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id, @Valid @RequestBody CampusOpeningExceptionsRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(places.openingExceptions(actor, id, body));
    }
    @PutMapping("/{id}/facilities") public ResponseEntity<CampusPlaceResponse> facilities(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id, @Valid @RequestBody CampusFacilityIdsRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(places.facilities(actor, id, body));
    }
}
