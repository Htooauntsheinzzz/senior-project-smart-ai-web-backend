package com.smartAiUniversityAssistant.seniorproject.feature.campus.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.entity.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.exception.CampusFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.mapper.CampusMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.repository.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import jakarta.persistence.criteria.Predicate;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.service.CampusBuildingService;

/** Buildings. The site row, then the building row, are locked before any dependency check, so a check cannot race a child insert. */
@Service
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_ADMIN')")
public class CampusBuildingServiceImpl implements CampusBuildingService {
    private final CampusBuildingRepository buildings;
    private final CampusSiteRepository sites;
    private final CampusRoomRepository rooms;
    private final CampusPlaceRepository places;
    private final CampusNavDestinationRepository destinations;
    private final CampusFacilityRepository facilities;
    private final CampusOpeningHoursSupport opening;
    private final CampusMapper mapper;
    private final CampusWriteSupport support;

    public CampusBuildingServiceImpl(CampusBuildingRepository buildings, CampusSiteRepository sites, CampusRoomRepository rooms,
            CampusPlaceRepository places, CampusNavDestinationRepository destinations, CampusFacilityRepository facilities,
            CampusOpeningHoursSupport opening, CampusMapper mapper, CampusWriteSupport support) {
        this.buildings = buildings;
        this.sites = sites;
        this.rooms = rooms;
        this.places = places;
        this.destinations = destinations;
        this.facilities = facilities;
        this.opening = opening;
        this.mapper = mapper;
        this.support = support;
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public CampusPageResponse<CampusBuildingListResponse> list(CampusPageQuery query) {
        Long siteId = query.id("siteId");
        CampusBuildingStatus status = query.enumValue("status", CampusBuildingStatus.class);
        Specification<CampusBuilding> specification = (root, criteria, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.isFalse(root.get("deleted")));
            if (siteId != null) predicates.add(cb.equal(root.get("site").get("id"), siteId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (query.search() != null) predicates.add(cb.or(CampusQuerySupport.contains(cb, root.get("buildingCode"), query.search()),
                    CampusQuerySupport.contains(cb, root.get("buildingName"), query.search())));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        var page = buildings.findAll(specification, query.pageable());
        var ids = page.getContent().stream().map(CampusBuilding::getId).toList();
        Map<Long, Long> counts = roomCounts(ids);
        var hours = opening.hoursForBuildings(ids);
        return query.response(page, b -> mapper.buildingRow(b, counts.getOrDefault(b.getId(), 0L),
                CampusOpeningCalculator.summary(hours.getOrDefault(b.getId(), List.of()))));
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public CampusBuildingResponse detail(long id) {
        return response(buildings.findByIdAndDeletedFalse(id).orElseThrow(CampusFailure::buildingNotFound));
    }

    @Override @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')") @Transactional(timeout = 15)
    public CampusBuildingResponse create(AuthenticatedUser actor, CampusBuildingCreateRequest request) {
        support.lockTimeout();
        CampusSite site = sites.findLiveForUpdate(request.siteId()).filter(CampusSite::isActive).orElseThrow(CampusFailure::invalidSite);
        String code = support.code(request.buildingCode());
        if (buildings.existsBySiteIdAndBuildingCodeAndIdNot(site.getId(), code, 0L)) throw CampusFailure.duplicate("The building code");
        var building = new CampusBuilding();
        building.setSite(site);
        building.setBuildingCode(code);
        fields(building, request.buildingName(), request.buildingNameTh(), request.description(), request.totalFloors(), request.areaSquareMeters());
        building.setStatus(request.status() == null ? CampusBuildingStatus.ACTIVE : request.status());
        building.setCreatedBy(actor.userId());
        building.setCreatedAt(support.now());
        try {
            return response(buildings.saveAndFlush(building));
        } catch (DataIntegrityViolationException e) {
            throw support.translate(e);
        }
    }

    @Override @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')") @Transactional(timeout = 15)
    public CampusBuildingResponse update(AuthenticatedUser actor, long id, CampusBuildingUpdateRequest request) {
        CampusBuilding building = locked(id);
        String code = support.code(request.buildingCode());
        if (buildings.existsBySiteIdAndBuildingCodeAndIdNot(building.getSite().getId(), code, id)) throw CampusFailure.duplicate("The building code");
        Short highest = rooms.highestFloor(id);
        if (highest != null && highest > request.totalFloors())
            throw CampusFailure.inUse("Rooms exist above the requested number of floors.");
        building.setBuildingCode(code);
        fields(building, request.buildingName(), request.buildingNameTh(), request.description(), request.totalFloors(), request.areaSquareMeters());
        if (request.status() != null) building.setStatus(request.status());
        touch(building, actor);
        try {
            buildings.flush();
        } catch (DataIntegrityViolationException e) {
            throw support.translate(e);
        }
        return response(building);
    }

    @Override @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')") @Transactional(timeout = 15)
    public CampusBuildingResponse status(AuthenticatedUser actor, long id, CampusBuildingStatusRequest request) {
        CampusBuilding building = locked(id);
        building.setStatus(request.status());
        touch(building, actor);
        buildings.flush();
        return response(building);
    }

    /** A delete never removes rooms, places or entrance mappings, so it is refused until they have been dealt with. */
    @Override @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')") @Transactional(timeout = 15)
    public void delete(AuthenticatedUser actor, long id) {
        CampusBuilding building = locked(id);
        if (rooms.existsByBuildingIdAndDeletedFalse(id)) throw CampusFailure.inUse("The building still has rooms.");
        if (places.existsByBuildingIdAndDeletedFalse(id)) throw CampusFailure.inUse("The building still has campus places.");
        if (destinations.existsByBuildingId(id)) throw CampusFailure.inUse("The building is still mapped to navigation nodes.");
        building.setDeleted(true);
        touch(building, actor);
        buildings.flush();
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public CampusBuildingStatisticsResponse statistics(Long siteId) {
        long active = 0, renovation = 0, inactive = 0;
        for (var row : buildings.countByStatus(siteId)) {
            switch (row.getStatus()) {
                case ACTIVE -> active = row.getTotal();
                case UNDER_RENOVATION -> renovation = row.getTotal();
                case INACTIVE -> inactive = row.getTotal();
            }
        }
        long totalRooms = rooms.countByStatus(siteId, null).stream().mapToLong(CampusRoomRepository.StatusCount::getTotal).sum();
        return new CampusBuildingStatisticsResponse(active + renovation + inactive, active, renovation, inactive, totalRooms);
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public List<CampusRoomResponse> rooms(long id) {
        buildings.findByIdAndDeletedFalse(id).orElseThrow(CampusFailure::buildingNotFound);
        var rows = rooms.findByBuildingIdAndDeletedFalseOrderByFloorNumberAscRoomNumberAscIdAsc(id);
        var owned = new HashMap<Long, List<CampusFacilityRef>>();
        if (!rows.isEmpty()) for (var row : facilities.forRooms(rows.stream().map(CampusRoom::getId).toList()))
            owned.computeIfAbsent(row.getOwnerId(), k -> new ArrayList<>()).add(new CampusFacilityRef(row.getId(), row.getCode(), row.getName()));
        return rows.stream().map(r -> mapper.room(r, owned.getOrDefault(r.getId(), List.of()))).toList();
    }

    @Override @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')") @Transactional(timeout = 15)
    public List<CampusOpeningHourDto> openingHours(AuthenticatedUser actor, long id, CampusOpeningHoursRequest request) {
        CampusBuilding building = locked(id);
        var result = opening.replaceHours(id, null, request);
        touch(building, actor);
        return result;
    }

    @Override @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')") @Transactional(timeout = 15)
    public List<CampusOpeningExceptionDto> openingExceptions(AuthenticatedUser actor, long id, CampusOpeningExceptionsRequest request) {
        CampusBuilding building = locked(id);
        var result = opening.replaceExceptions(id, null, request);
        touch(building, actor);
        return result;
    }

    private CampusBuilding locked(long id) {
        support.lockTimeout();
        return buildings.findLiveForUpdate(id).orElseThrow(CampusFailure::buildingNotFound);
    }
    private void fields(CampusBuilding building, String name, String nameTh, String description, int floors, java.math.BigDecimal area) {
        building.setBuildingName(support.required(name));
        building.setBuildingNameTh(support.optional(nameTh));
        building.setDescription(support.optional(description));
        building.setTotalFloors((short) floors);
        building.setAreaSquareMeters(area);
    }
    private void touch(CampusBuilding building, AuthenticatedUser actor) {
        building.setUpdatedBy(actor.userId());
        building.setUpdatedAt(support.now());
    }
    private Map<Long, Long> roomCounts(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        var counts = new HashMap<Long, Long>();
        for (var row : rooms.countForBuildings(ids)) counts.put(row.getBuildingId(), row.getTotal());
        return counts;
    }
    private CampusBuildingResponse response(CampusBuilding building) {
        var hours = opening.buildingHours(building.getId());
        return mapper.building(building, roomCounts(List.of(building.getId())).getOrDefault(building.getId(), 0L),
                CampusOpeningCalculator.summary(hours), opening.dtos(hours), opening.exceptionDtos(opening.buildingExceptions(building.getId())));
    }
}
