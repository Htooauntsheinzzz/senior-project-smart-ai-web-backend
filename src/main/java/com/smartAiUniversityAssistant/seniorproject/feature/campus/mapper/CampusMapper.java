package com.smartAiUniversityAssistant.seniorproject.feature.campus.mapper;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.entity.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** Entity to DTO conversion. Callers load related rows in bulk and pass them in, so no mapping triggers a query per row. */
@Component
public class CampusMapper {
    public CampusSiteResponse site(CampusSite s) {
        return new CampusSiteResponse(s.getId(), s.getSiteCode(), s.getSiteName(), s.getDescription(), s.getTimezone(),
                s.getCenterLatitude(), s.getCenterLongitude(), s.isActive(), s.getCreatedBy(), instant(s.getCreatedAt()),
                s.getUpdatedBy(), instant(s.getUpdatedAt()));
    }
    public CampusCategoryResponse category(CampusCategory c) {
        return new CampusCategoryResponse(c.getId(), c.getCategoryCode(), c.getCategoryName(), c.getMobileGroup(), c.getDescription(),
                c.isActive(), c.getCreatedBy(), instant(c.getCreatedAt()), c.getUpdatedBy(), instant(c.getUpdatedAt()));
    }
    public CampusFacilityResponse facility(CampusFacility f) {
        return new CampusFacilityResponse(f.getId(), f.getFacilityCode(), f.getFacilityName(), f.getDescription(), f.isActive(),
                f.getCreatedBy(), instant(f.getCreatedAt()), f.getUpdatedBy(), instant(f.getUpdatedAt()));
    }
    public CampusBuildingListResponse buildingRow(CampusBuilding b, long rooms, String hoursSummary) {
        return new CampusBuildingListResponse(b.getId(), b.getSite().getId(), b.getBuildingCode(), b.getBuildingName(), b.getAreaSquareMeters(),
                b.getTotalFloors(), rooms, hoursSummary, b.getStatus(), instant(b.getCreatedAt()), instant(b.getUpdatedAt()));
    }
    public CampusBuildingResponse building(CampusBuilding b, long rooms, String hoursSummary, List<CampusOpeningHourDto> hours,
            List<CampusOpeningExceptionDto> exceptions) {
        return new CampusBuildingResponse(b.getId(), b.getSite().getId(), b.getSite().getSiteName(), b.getBuildingCode(), b.getBuildingName(),
                b.getBuildingNameTh(), b.getDescription(), b.getTotalFloors(), b.getAreaSquareMeters(), rooms, hoursSummary, hours, exceptions,
                b.getStatus(), b.getCreatedBy(), instant(b.getCreatedAt()), b.getUpdatedBy(), instant(b.getUpdatedAt()));
    }
    public CampusRoomResponse room(CampusRoom r, List<CampusFacilityRef> facilities) {
        CampusBuilding b = r.getBuilding();
        return new CampusRoomResponse(r.getId(), b.getId(), b.getBuildingCode(), b.getBuildingName(), r.getRoomNumber(), r.getRoomName(),
                r.getFloorNumber(), r.getRoomType(), r.getCapacity(), r.getStatus(), facilities, r.getCreatedBy(), instant(r.getCreatedAt()),
                r.getUpdatedBy(), instant(r.getUpdatedAt()));
    }
    public CampusPlaceResponse place(CampusPlace p, List<CampusFacilityRef> facilities, String hoursSummary,
            List<CampusOpeningHourDto> hours, List<CampusOpeningExceptionDto> exceptions) {
        CampusCategory c = p.getCategory();
        CampusBuilding b = p.getBuilding();
        return new CampusPlaceResponse(p.getId(), p.getSite().getId(),
                new CampusPlaceResponse.Category(c.getId(), c.getCategoryCode(), c.getCategoryName(), c.getMobileGroup()),
                b == null ? null : new CampusNamedRef(b.getId(), b.getBuildingCode(), b.getBuildingName()),
                p.getPlaceCode(), p.getPlaceName(), p.getPlaceNameTh(), p.getDescription(), p.getFloorLabel(), p.getPhoneNumber(),
                p.isPopular(), p.isVisibleOnMobile(), p.getStatus(), facilities, hoursSummary, hours, exceptions,
                p.getCreatedBy(), instant(p.getCreatedAt()), p.getUpdatedBy(), instant(p.getUpdatedAt()));
    }
    public CampusNodeResponse node(CampusNavNode n) {
        return new CampusNodeResponse(n.getId(), n.getSite().getId(), n.getNodeCode(), n.getNodeName(), n.getNodeType(), n.getLatitude(),
                n.getLongitude(), n.isAccessible(), n.isActive(), n.getCreatedBy(), instant(n.getCreatedAt()), n.getUpdatedBy(), instant(n.getUpdatedAt()));
    }
    public CampusEdgeResponse edge(CampusNavEdge e, Integer walkMinutes, List<CampusCoordinate> points) {
        return new CampusEdgeResponse(e.getId(), e.getSite().getId(), e.getEdgeCode(), nodeRef(e.getStartNode()), nodeRef(e.getEndNode()),
                e.getDistanceMeters(), walkMinutes, e.getSurfaceType(), e.isWheelchairAccessible(), e.isBidirectional(), e.getStatus(),
                !points.isEmpty(), points, e.getCreatedBy(), instant(e.getCreatedAt()), e.getUpdatedBy(), instant(e.getUpdatedAt()));
    }
    public CampusDestinationResponse destination(CampusNavDestination d) {
        return new CampusDestinationResponse(d.getId(), d.getNode().getSite().getId(), nodeRef(d.getNode()),
                d.getBuilding() == null ? null : new CampusNamedRef(d.getBuilding().getId(), d.getBuilding().getBuildingCode(), d.getBuilding().getBuildingName()),
                d.getPlace() == null ? null : new CampusNamedRef(d.getPlace().getId(), d.getPlace().getPlaceCode(), d.getPlace().getPlaceName()),
                d.isPrimary(), d.isAccessibleEntrance(), d.getCreatedBy(), instant(d.getCreatedAt()), d.getUpdatedBy(), instant(d.getUpdatedAt()));
    }
    public CampusNamedRef nodeRef(CampusNavNode n) { return new CampusNamedRef(n.getId(), n.getNodeCode(), n.getNodeName()); }
    public CampusOpeningHourDto hour(CampusOpeningHour h) {
        return new CampusOpeningHourDto((int) h.getDayOfWeek(), h.getOpensAt(), h.getClosesAt(), h.isClosed());
    }
    public CampusOpeningExceptionDto exception(CampusOpeningException e) {
        return new CampusOpeningExceptionDto(e.getExceptionDate(), e.isClosed(), e.getOpensAt(), e.getClosesAt(), e.getReason());
    }
    private Instant instant(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC); }
}
