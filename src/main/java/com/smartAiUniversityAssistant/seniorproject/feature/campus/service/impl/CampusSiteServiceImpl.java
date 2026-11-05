package com.smartAiUniversityAssistant.seniorproject.feature.campus.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.entity.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.exception.CampusFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.mapper.CampusMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.repository.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import jakarta.persistence.criteria.Predicate;
import java.time.*;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.service.CampusSiteService;

/** Campus sites. No campus row is ever seeded: names and coordinates are entered here from verified data. */
@Service
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_ADMIN')")
public class CampusSiteServiceImpl implements CampusSiteService {
    private final CampusSiteRepository sites;
    private final CampusBuildingRepository buildings;
    private final CampusPlaceRepository places;
    private final CampusNavNodeRepository nodes;
    private final CampusNavEdgeRepository edges;
    private final CampusMapper mapper;
    private final CampusWriteSupport support;

    public CampusSiteServiceImpl(CampusSiteRepository sites, CampusBuildingRepository buildings, CampusPlaceRepository places,
            CampusNavNodeRepository nodes, CampusNavEdgeRepository edges, CampusMapper mapper, CampusWriteSupport support) {
        this.sites = sites;
        this.buildings = buildings;
        this.places = places;
        this.nodes = nodes;
        this.edges = edges;
        this.mapper = mapper;
        this.support = support;
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public CampusPageResponse<CampusSiteResponse> list(CampusPageQuery query) {
        Boolean active = query.bool("isActive");
        Specification<CampusSite> specification = (root, criteria, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.isFalse(root.get("deleted")));
            if (active != null) predicates.add(cb.equal(root.get("active"), active));
            if (query.search() != null) predicates.add(cb.or(CampusQuerySupport.contains(cb, root.get("siteCode"), query.search()),
                    CampusQuerySupport.contains(cb, root.get("siteName"), query.search())));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return query.response(sites.findAll(specification, query.pageable()), mapper::site);
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public CampusSiteResponse detail(long id) {
        return mapper.site(sites.findByIdAndDeletedFalse(id).orElseThrow(CampusFailure::campusNotFound));
    }

    @Override @PreAuthorize("hasRole('SUPER_ADMIN')") @Transactional(timeout = 15)
    public CampusSiteResponse create(AuthenticatedUser actor, CampusSiteRequest request) {
        var site = new CampusSite();
        apply(site, request, 0L);
        site.setActive(request.isActive() == null || request.isActive());
        site.setCreatedBy(actor.userId());
        site.setCreatedAt(support.now());
        try {
            return mapper.site(sites.saveAndFlush(site));
        } catch (DataIntegrityViolationException e) {
            throw support.translate(e);
        }
    }

    @Override @PreAuthorize("hasRole('SUPER_ADMIN')") @Transactional(timeout = 15)
    public CampusSiteResponse update(AuthenticatedUser actor, long id, CampusSiteRequest request) {
        support.lockTimeout();
        CampusSite site = sites.findLiveForUpdate(id).orElseThrow(CampusFailure::campusNotFound);
        apply(site, request, id);
        if (request.isActive() != null) site.setActive(request.isActive());
        site.setUpdatedBy(actor.userId());
        site.setUpdatedAt(support.now());
        try {
            sites.flush();
        } catch (DataIntegrityViolationException e) {
            throw support.translate(e);
        }
        return mapper.site(site);
    }

    @Override @PreAuthorize("hasRole('SUPER_ADMIN')") @Transactional(timeout = 15)
    public void delete(AuthenticatedUser actor, long id) {
        support.lockTimeout();
        CampusSite site = sites.findLiveForUpdate(id).orElseThrow(CampusFailure::campusNotFound);
        if (buildings.existsBySiteIdAndDeletedFalse(id) || places.existsBySiteIdAndDeletedFalse(id)
                || nodes.existsBySiteIdAndDeletedFalse(id) || edges.existsBySiteIdAndDeletedFalse(id))
            throw CampusFailure.inUse("The campus still has buildings, places or navigation data.");
        site.setDeleted(true);
        site.setUpdatedBy(actor.userId());
        site.setUpdatedAt(support.now());
        sites.flush();
    }

    private void apply(CampusSite site, CampusSiteRequest request, long id) {
        String code = support.code(request.siteCode());
        if (sites.existsBySiteCodeAndIdNot(code, id)) throw CampusFailure.duplicate("The campus code");
        // Center is optional, but a half-filled pair cannot place the campus on a map.
        if (request.centerLatitude() != null || request.centerLongitude() != null)
            support.coordinates(request.centerLatitude(), request.centerLongitude());
        site.setSiteCode(code);
        site.setSiteName(support.required(request.siteName()));
        site.setDescription(support.optional(request.description()));
        site.setCenterLatitude(request.centerLatitude());
        site.setCenterLongitude(request.centerLongitude());
        String timezone = support.optional(request.timezone());
        if (timezone != null) {
            try {
                ZoneId.of(timezone);
            } catch (DateTimeException e) {
                throw CampusFailure.invalid("The timezone is invalid.");
            }
            site.setTimezone(timezone);
        } else if (site.getTimezone() == null) {
            site.setTimezone("Asia/Bangkok");
        }
    }
}
