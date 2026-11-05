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
@RequestMapping("/api/v1/admin/campus/sites")
public class AdminCampusSiteController {
    private static final Set<String> SORT = Set.of("id", "siteCode", "siteName", "createdAt");
    private final CampusSiteService sites;
    public AdminCampusSiteController(CampusSiteService sites) { this.sites = sites; }

    @GetMapping public ResponseEntity<CampusPageResponse<CampusSiteResponse>> list(HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sites.list(CampusPageQuery.from(request, Set.of("isActive"), SORT, "siteName,asc")));
    }
    @GetMapping("/{id}") public ResponseEntity<CampusSiteResponse> detail(@PathVariable long id) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sites.detail(id)); }
    @PostMapping public ResponseEntity<CampusSiteResponse> create(@AuthenticationPrincipal AuthenticatedUser actor, @Valid @RequestBody CampusSiteRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(sites.create(actor, body));
    }
    @PutMapping("/{id}") public ResponseEntity<CampusSiteResponse> update(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id, @Valid @RequestBody CampusSiteRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sites.update(actor, id, body));
    }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        sites.delete(actor, id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
