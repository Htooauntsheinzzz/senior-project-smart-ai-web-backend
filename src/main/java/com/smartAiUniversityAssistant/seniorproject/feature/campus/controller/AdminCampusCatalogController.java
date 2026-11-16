package com.smartAiUniversityAssistant.seniorproject.feature.campus.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.service.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/campus")
public class AdminCampusCatalogController {
    private final CampusCatalogService catalog;
    public AdminCampusCatalogController(CampusCatalogService catalog) { this.catalog = catalog; }

    @GetMapping("/categories") public ResponseEntity<List<CampusCategoryResponse>> categories() { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(catalog.categories()); }
    @PostMapping("/categories") public ResponseEntity<CampusCategoryResponse> createCategory(@AuthenticationPrincipal AuthenticatedUser actor, @Valid @RequestBody CampusCategoryRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(catalog.createCategory(actor, body));
    }
    @PutMapping("/categories/{id}") public ResponseEntity<CampusCategoryResponse> updateCategory(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id, @Valid @RequestBody CampusCategoryRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(catalog.updateCategory(actor, id, body));
    }
    @GetMapping("/facilities") public ResponseEntity<List<CampusFacilityResponse>> facilities() { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(catalog.facilities(false)); }
    @PostMapping("/facilities") public ResponseEntity<CampusFacilityResponse> createFacility(@AuthenticationPrincipal AuthenticatedUser actor, @Valid @RequestBody CampusFacilityRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(catalog.createFacility(actor, body));
    }
    @PutMapping("/facilities/{id}") public ResponseEntity<CampusFacilityResponse> updateFacility(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id, @Valid @RequestBody CampusFacilityRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(catalog.updateFacility(actor, id, body));
    }
}
