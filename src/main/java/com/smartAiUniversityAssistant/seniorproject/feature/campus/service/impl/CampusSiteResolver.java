package com.smartAiUniversityAssistant.seniorproject.feature.campus.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.entity.CampusSite;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.exception.CampusFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.repository.CampusSiteRepository;
import org.springframework.stereotype.Component;

/** A missing siteId is accepted only while exactly one campus exists, so the single-campus mobile app need not send it. */
@Component
class CampusSiteResolver {
    private final CampusSiteRepository sites;
    CampusSiteResolver(CampusSiteRepository sites) { this.sites = sites; }

    CampusSite active(Long requested) {
        if (requested != null) return sites.findByIdAndDeletedFalseAndActiveTrue(requested).orElseThrow(CampusFailure::campusNotFound);
        return onlyActive();
    }
    CampusSite live(Long requested) {
        if (requested != null) return sites.findByIdAndDeletedFalse(requested).orElseThrow(CampusFailure::campusNotFound);
        return onlyActive();
    }
    private CampusSite onlyActive() {
        var active = sites.findByDeletedFalseAndActiveTrueOrderBySiteNameAscIdAsc();
        if (active.isEmpty()) throw CampusFailure.campusNotFound();
        if (active.size() > 1) throw CampusFailure.invalid("siteId is required when several campuses exist.");
        return active.get(0);
    }
}
