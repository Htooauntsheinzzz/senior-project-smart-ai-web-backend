package com.smartAiUniversityAssistant.seniorproject.feature.campus.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.entity.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.*;
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
import com.smartAiUniversityAssistant.seniorproject.feature.campus.service.CampusPlaceService;

/** Campus places. Coordinates are never accepted here; a place gets them only through a mapped navigation destination. */
@Service
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_ADMIN')")
public class CampusPlaceServiceImpl implements CampusPlaceService {
    private final CampusPlaceRepository places;
    private final CampusSiteRepository sites;
    private final CampusCategoryRepository categories;
    private final CampusBuildingRepository buildings;
    private final CampusFacilityRepository facilities;
    private final CampusNavDestinationRepository destinations;
    private final CampusOpeningHoursSupport opening;
    private final CampusMapper mapper;
    private final CampusWriteSupport support;
    private final Clock clock;

    public CampusPlaceServiceImpl(CampusPlaceRepository places, CampusSiteRepository sites, CampusCategoryRepository categories,
            CampusBuildingRepository buildings, CampusFacilityRepository facilities, CampusNavDestinationRepository destinations,
            CampusOpeningHoursSupport opening, CampusMapper mapper, CampusWriteSupport support, Clock clock) {
        this.places = places;
        this.sites = sites;
        this.categories = categories;
        this.buildings = buildings;
        this.facilities = facilities;
        this.destinations = destinations;
        this.opening = opening;
        this.mapper = mapper;
        this.support = support;
        this.clock = clock;
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public CampusPageResponse<CampusPlaceResponse> list(CampusPageQuery query) {
        Long siteId = query.id("siteId");
        Long categoryId = query.id("categoryId");
        Long buildingId = query.id("buildingId");
        CampusPlaceStatus status = query.enumValue("status", CampusPlaceStatus.class);
        CampusMobileGroup group = query.enumValue("mobileGroup", CampusMobileGroup.class);
        Boolean popular = query.bool("isPopular");
        Boolean visible = query.bool("isVisibleOnMobile");
        Specification<CampusPlace> specification = (root, criteria, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.isFalse(root.get("deleted")));
            if (siteId != null) predicates.add(cb.equal(root.get("site").get("id"), siteId));
            if (categoryId != null) predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            if (buildingId != null) predicates.add(cb.equal(root.get("building").get("id"), buildingId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (group != null) predicates.add(cb.equal(root.get("category").get("mobileGroup"), group));
            if (popular != null) predicates.add(cb.equal(root.get("popular"), popular));
            if (visible != null) predicates.add(cb.equal(root.get("visibleOnMobile"), visible));
            if (query.search() != null) predicates.add(cb.or(CampusQuerySupport.contains(cb, root.get("placeCode"), query.search()),
                    CampusQuerySupport.contains(cb, root.get("placeName"), query.search())));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        var page = places.findAll(specification, query.pageable());
        var ids = page.getContent().stream().map(CampusPlace::getId).toList();
        var owned = new HashMap<Long, List<CampusFacilityRef>>();
        if (!ids.isEmpty()) for (var row : facilities.forPlaces(ids))
            owned.computeIfAbsent(row.getOwnerId(), k -> new ArrayList<>()).add(new CampusFacilityRef(row.getId(), row.getCode(), row.getName()));
        var hours = opening.hoursForPlaces(ids);
        // The list leaves the full schedule to the detail endpoint and shows the summary only.
        return query.response(page, p -> mapper.place(p, owned.getOrDefault(p.getId(), List.of()),
                CampusOpeningCalculator.summary(hours.getOrDefault(p.getId(), List.of())), null, null));
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public CampusPlaceResponse detail(long id) {
        return response(places.findByIdAndDeletedFalse(id).orElseThrow(CampusFailure::placeNotFound));
    }

    @Override @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')") @Transactional(timeout = 15)
    public CampusPlaceResponse create(AuthenticatedUser actor, CampusPlaceCreateRequest request) {
        support.lockTimeout();
        CampusSite site = sites.findLiveForUpdate(request.siteId()).filter(CampusSite::isActive).orElseThrow(CampusFailure::invalidSite);
        var place = new CampusPlace();
        place.setSite(site);
        apply(place, 0L, request.categoryId(), request.buildingId(), request.placeCode(), request.placeName(), request.placeNameTh(),
                request.description(), request.floorLabel(), request.phoneNumber(), request.facilityIds());
        place.setPopular(Boolean.TRUE.equals(request.isPopular()));
        place.setVisibleOnMobile(request.isVisibleOnMobile() == null || request.isVisibleOnMobile());
        place.setStatus(request.status() == null ? CampusPlaceStatus.ACTIVE : request.status());
        place.setCreatedBy(actor.userId());
        place.setCreatedAt(support.now());
        try {
            return response(places.saveAndFlush(place));
        } catch (DataIntegrityViolationException e) {
            throw support.translate(e);
        }
    }

    @Override @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')") @Transactional(timeout = 15)
    public CampusPlaceResponse update(AuthenticatedUser actor, long id, CampusPlaceUpdateRequest request) {
        CampusPlace place = locked(id);
        apply(place, id, request.categoryId(), request.buildingId(), request.placeCode(), request.placeName(), request.placeNameTh(),
                request.description(), request.floorLabel(), request.phoneNumber(), request.facilityIds());
        if (request.isPopular() != null) place.setPopular(request.isPopular());
        if (request.isVisibleOnMobile() != null) place.setVisibleOnMobile(request.isVisibleOnMobile());
        if (request.status() != null) place.setStatus(request.status());
        return saved(place, actor);
    }

    @Override @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')") @Transactional(timeout = 15)
    public CampusPlaceResponse status(AuthenticatedUser actor, long id, CampusPlaceStatusRequest request) {
        CampusPlace place = locked(id);
        place.setStatus(request.status());
        return saved(place, actor);
    }

    @Override @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')") @Transactional(timeout = 15)
    public CampusPlaceResponse visibility(AuthenticatedUser actor, long id, CampusPlaceVisibilityRequest request) {
        CampusPlace place = locked(id);
        place.setVisibleOnMobile(request.isVisibleOnMobile());
        return saved(place, actor);
    }

    @Override @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')") @Transactional(timeout = 15)
    public CampusPlaceResponse popular(AuthenticatedUser actor, long id, CampusPlacePopularRequest request) {
        CampusPlace place = locked(id);
        place.setPopular(request.isPopular());
        return saved(place, actor);
    }

    @Override @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')") @Transactional(timeout = 15)
    public CampusPlaceResponse facilities(AuthenticatedUser actor, long id, CampusFacilityIdsRequest request) {
        CampusPlace place = locked(id);
        setFacilities(place, request.facilityIds());
        return saved(place, actor);
    }

    @Override @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')") @Transactional(timeout = 15)
    public void delete(AuthenticatedUser actor, long id) {
        CampusPlace place = locked(id);
        if (destinations.existsByPlaceId(id)) throw CampusFailure.inUse("The place is still mapped to navigation nodes.");
        place.setDeleted(true);
        touch(place, actor);
        places.flush();
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public CampusPlaceStatisticsResponse statistics(Long siteId) {
        var counts = places.counts(siteId);
        var active = places.liveWithStatus(CampusPlaceStatus.ACTIVE, siteId);
        var ids = active.stream().map(CampusPlace::getId).toList();
        var now = new HashMap<Long, ZonedDateTime>();
        for (CampusPlace place : active) now.put(place.getId(), clock.instant().atZone(ZoneId.of(place.getSite().getTimezone())));
        var hours = opening.hoursForPlaces(ids);
        var dates = now.values().stream().map(ZonedDateTime::toLocalDate).distinct().toList();
        var exceptions = opening.exceptionsForPlaces(ids, dates);
        long open = 0, unpublished = 0;
        for (CampusPlace place : active) {
            ZonedDateTime at = now.get(place.getId());
            var exception = exceptions.getOrDefault(place.getId(), Map.of()).get(at.toLocalDate());
            var state = CampusOpeningCalculator.status(true, CampusPlaceStatus.ACTIVE.name(), hours.getOrDefault(place.getId(), List.of()), exception, at);
            if (Boolean.TRUE.equals(state.expectedOpen())) open++;
            if (!hours.containsKey(place.getId())) unpublished++;
        }
        return new CampusPlaceStatisticsResponse(counts.getTotal(), counts.getActive(), counts.getTemporarilyClosed(), counts.getInactive(),
                counts.getPopular(), counts.getVisibleOnMobile(), open, unpublished);
    }

    @Override @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')") @Transactional(timeout = 15)
    public List<CampusOpeningHourDto> openingHours(AuthenticatedUser actor, long id, CampusOpeningHoursRequest request) {
        CampusPlace place = locked(id);
        var result = opening.replaceHours(null, id, request);
        touch(place, actor);
        return result;
    }

    @Override @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')") @Transactional(timeout = 15)
    public List<CampusOpeningExceptionDto> openingExceptions(AuthenticatedUser actor, long id, CampusOpeningExceptionsRequest request) {
        CampusPlace place = locked(id);
        var result = opening.replaceExceptions(null, id, request);
        touch(place, actor);
        return result;
    }

    private void apply(CampusPlace place, long id, long categoryId, Long buildingId, String code, String name, String nameTh,
            String description, String floorLabel, String phone, List<Long> facilityIds) {
        support.lockTimeout();
        long siteId = place.getSite().getId();
        String placeCode = support.code(code);
        if (places.existsBySiteIdAndPlaceCodeAndIdNot(siteId, placeCode, id)) throw CampusFailure.duplicate("The place code");
        place.setCategory(categories.findUsableForShare(categoryId).orElseThrow(CampusFailure::invalidCategory));
        if (buildingId == null) {
            place.setBuilding(null);
        } else {
            CampusBuilding building = buildings.findLiveForUpdate(buildingId).orElseThrow(CampusFailure::invalidBuilding);
            if (!building.getSite().getId().equals(siteId)) throw CampusFailure.invalidBuilding();
            place.setBuilding(building);
        }
        place.setPlaceCode(placeCode);
        place.setPlaceName(support.required(name));
        place.setPlaceNameTh(support.optional(nameTh));
        place.setDescription(support.optional(description));
        place.setFloorLabel(support.optional(floorLabel));
        place.setPhoneNumber(support.phone(phone));
        setFacilities(place, facilityIds);
    }
    private void setFacilities(CampusPlace place, List<Long> facilityIds) {
        var ids = support.distinctIds(facilityIds);
        var chosen = ids.isEmpty() ? List.<CampusFacility>of() : facilities.findUsableForShare(ids);
        if (chosen.size() != ids.size()) throw CampusFailure.invalidFacility();
        place.getFacilities().clear();
        place.getFacilities().addAll(chosen);
    }
    private CampusPlace locked(long id) {
        support.lockTimeout();
        return places.findLiveForUpdate(id).orElseThrow(CampusFailure::placeNotFound);
    }
    private void touch(CampusPlace place, AuthenticatedUser actor) {
        place.setUpdatedBy(actor.userId());
        place.setUpdatedAt(support.now());
    }
    private CampusPlaceResponse saved(CampusPlace place, AuthenticatedUser actor) {
        touch(place, actor);
        try {
            places.flush();
        } catch (DataIntegrityViolationException e) {
            throw support.translate(e);
        }
        return response(place);
    }
    private CampusPlaceResponse response(CampusPlace place) {
        var refs = place.getFacilities().stream()
                .sorted(Comparator.comparing(CampusFacility::getFacilityName).thenComparing(CampusFacility::getId))
                .map(f -> new CampusFacilityRef(f.getId(), f.getFacilityCode(), f.getFacilityName())).toList();
        var hours = opening.placeHours(place.getId());
        return mapper.place(place, refs, CampusOpeningCalculator.summary(hours), opening.dtos(hours),
                opening.exceptionDtos(opening.placeExceptions(place.getId())));
    }
}
