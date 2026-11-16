package com.smartAiUniversityAssistant.seniorproject.feature.campus.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.service.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/campus/map")
public class AdminCampusMapController {
    private final CampusMapService map;
    public AdminCampusMapController(CampusMapService map) { this.map = map; }

    @GetMapping public ResponseEntity<CampusAdminMapResponse> map(@RequestParam(required = false) Long siteId,
            @RequestParam(defaultValue = "true") boolean includeBuildings, @RequestParam(defaultValue = "true") boolean includePlaces,
            @RequestParam(defaultValue = "true") boolean includeNodes, @RequestParam(defaultValue = "true") boolean includeEdges,
            @RequestParam(required = false) Long categoryId, @RequestParam(required = false) String status) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(map.map(siteId, includeBuildings, includePlaces, includeNodes, includeEdges, categoryId, status));
    }
}
