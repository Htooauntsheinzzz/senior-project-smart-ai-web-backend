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
import com.smartAiUniversityAssistant.seniorproject.feature.campus.service.CampusRoomService;
import com.smartAiUniversityAssistant.seniorproject.config.CampusProperties;

@Service
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_ADMIN')")
public class CampusRoomServiceImpl implements CampusRoomService {
    private final CampusRoomRepository rooms;
    private final CampusBuildingRepository buildings;
    private final CampusFacilityRepository facilities;
    private final CampusMapper mapper;
    private final CampusWriteSupport support;
    private final CampusProperties properties;

    public CampusRoomServiceImpl(CampusRoomRepository rooms, CampusBuildingRepository buildings, CampusFacilityRepository facilities,
            CampusMapper mapper, CampusWriteSupport support, CampusProperties properties) {
        this.rooms = rooms;
        this.buildings = buildings;
        this.facilities = facilities;
        this.mapper = mapper;
        this.support = support;
        this.properties = properties;
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public CampusPageResponse<CampusRoomResponse> list(CampusPageQuery query) {
        Long buildingId = query.id("buildingId");
        Integer floor = query.integer("floorNumber");
        CampusRoomType type = query.enumValue("roomType", CampusRoomType.class);
        CampusRoomStatus status = query.enumValue("status", CampusRoomStatus.class);
        Specification<CampusRoom> specification = (root, criteria, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.isFalse(root.get("deleted")));
            if (buildingId != null) predicates.add(cb.equal(root.get("building").get("id"), buildingId));
            if (floor != null) predicates.add(cb.equal(root.get("floorNumber"), floor.shortValue()));
            if (type != null) predicates.add(cb.equal(root.get("roomType"), type));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (query.search() != null) predicates.add(cb.or(CampusQuerySupport.contains(cb, root.get("roomNumber"), query.search()),
                    CampusQuerySupport.contains(cb, root.get("roomName"), query.search()),
                    CampusQuerySupport.contains(cb, root.get("building").get("buildingName"), query.search())));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        var page = rooms.findAll(specification, query.pageable());
        var owned = facilityRefs(page.getContent().stream().map(CampusRoom::getId).toList());
        return query.response(page, r -> mapper.room(r, owned.getOrDefault(r.getId(), List.of())));
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public CampusRoomResponse detail(long id) {
        return response(rooms.findByIdAndDeletedFalse(id).orElseThrow(CampusFailure::roomNotFound));
    }

    @Override @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')") @Transactional(timeout = 15)
    public CampusRoomResponse create(AuthenticatedUser actor, CampusRoomRequest request) {
        var room = new CampusRoom();
        apply(room, request, 0L);
        room.setStatus(request.status() == null ? CampusRoomStatus.AVAILABLE : request.status());
        room.setCreatedBy(actor.userId());
        room.setCreatedAt(support.now());
        try {
            return response(rooms.saveAndFlush(room));
        } catch (DataIntegrityViolationException e) {
            throw support.translate(e);
        }
    }

    @Override @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')") @Transactional(timeout = 15)
    public CampusRoomResponse update(AuthenticatedUser actor, long id, CampusRoomRequest request) {
        CampusRoom room = rooms.findByIdAndDeletedFalse(id).orElseThrow(CampusFailure::roomNotFound);
        apply(room, request, id);
        if (request.status() != null) room.setStatus(request.status());
        touch(room, actor);
        try {
            rooms.flush();
        } catch (DataIntegrityViolationException e) {
            throw support.translate(e);
        }
        return response(room);
    }

    @Override @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')") @Transactional(timeout = 15)
    public CampusRoomResponse status(AuthenticatedUser actor, long id, CampusRoomStatusRequest request) {
        CampusRoom room = rooms.findByIdAndDeletedFalse(id).orElseThrow(CampusFailure::roomNotFound);
        room.setStatus(request.status());
        touch(room, actor);
        rooms.flush();
        return response(room);
    }

    @Override @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')") @Transactional(timeout = 15)
    public void delete(AuthenticatedUser actor, long id) {
        CampusRoom room = rooms.findByIdAndDeletedFalse(id).orElseThrow(CampusFailure::roomNotFound);
        room.setDeleted(true);
        touch(room, actor);
        rooms.flush();
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public CampusRoomStatisticsResponse statistics(Long siteId, Long buildingId) {
        var counts = new EnumMap<CampusRoomStatus, Long>(CampusRoomStatus.class);
        for (var row : rooms.countByStatus(siteId, buildingId)) counts.put(row.getStatus(), row.getTotal());
        return new CampusRoomStatisticsResponse(counts.values().stream().mapToLong(Long::longValue).sum(),
                counts.getOrDefault(CampusRoomStatus.AVAILABLE, 0L), counts.getOrDefault(CampusRoomStatus.OCCUPIED, 0L),
                counts.getOrDefault(CampusRoomStatus.RESERVED, 0L), counts.getOrDefault(CampusRoomStatus.MAINTENANCE, 0L),
                counts.getOrDefault(CampusRoomStatus.INACTIVE, 0L), "DECLARED_STATUS");
    }

    private void apply(CampusRoom room, CampusRoomRequest request, long id) {
        support.lockTimeout();
        // The building lock keeps the floor check and a concurrent building delete or floor reduction consistent.
        CampusBuilding building = buildings.findLiveForUpdate(request.buildingId()).orElseThrow(CampusFailure::invalidBuilding);
        if (request.floorNumber() < properties.lowestFloorNumber() || request.floorNumber() > building.getTotalFloors())
            throw CampusFailure.invalidFloor();
        String number = support.roomNumber(request.roomNumber());
        if (rooms.existsByBuildingIdAndRoomNumberAndIdNot(building.getId(), number, id)) throw CampusFailure.duplicate("The room number");
        var ids = support.distinctIds(request.facilityIds());
        var chosen = ids.isEmpty() ? List.<CampusFacility>of() : facilities.findUsableForShare(ids);
        if (chosen.size() != ids.size()) throw CampusFailure.invalidFacility();
        room.setBuilding(building);
        room.setRoomNumber(number);
        room.setRoomName(support.optional(request.roomName()));
        room.setFloorNumber(request.floorNumber().shortValue());
        room.setRoomType(request.roomType());
        room.setCapacity(request.capacity());
        room.getFacilities().clear();
        room.getFacilities().addAll(chosen);
    }
    private void touch(CampusRoom room, AuthenticatedUser actor) {
        room.setUpdatedBy(actor.userId());
        room.setUpdatedAt(support.now());
    }
    private Map<Long, List<CampusFacilityRef>> facilityRefs(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        var owned = new HashMap<Long, List<CampusFacilityRef>>();
        for (var row : facilities.forRooms(ids))
            owned.computeIfAbsent(row.getOwnerId(), k -> new ArrayList<>()).add(new CampusFacilityRef(row.getId(), row.getCode(), row.getName()));
        return owned;
    }
    private CampusRoomResponse response(CampusRoom room) {
        return mapper.room(room, room.getFacilities().stream()
                .sorted(Comparator.comparing(CampusFacility::getFacilityName).thenComparing(CampusFacility::getId))
                .map(f -> new CampusFacilityRef(f.getId(), f.getFacilityCode(), f.getFacilityName())).toList());
    }
}
