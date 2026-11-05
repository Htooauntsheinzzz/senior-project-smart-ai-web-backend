package com.smartAiUniversityAssistant.seniorproject.feature.campus.exception;

public class CampusFailure extends RuntimeException {
    private final int status;
    private final String code;
    public CampusFailure(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public int status() { return status; }
    public String code() { return code; }

    public static CampusFailure invalid() { return new CampusFailure(400, "VALIDATION_ERROR", "The request is invalid."); }
    public static CampusFailure invalid(String message) { return new CampusFailure(400, "VALIDATION_ERROR", message); }
    public static CampusFailure invalidCoordinates() {
        return new CampusFailure(400, "CAMPUS_INVALID_COORDINATES", "Latitude must be between -90 and 90 and longitude between -180 and 180.");
    }
    public static CampusFailure campusNotFound() { return new CampusFailure(404, "CAMPUS_NOT_FOUND", "The requested campus does not exist."); }
    public static CampusFailure locationNotFound() { return new CampusFailure(404, "CAMPUS_LOCATION_NOT_FOUND", "The requested campus location does not exist."); }
    public static CampusFailure buildingNotFound() { return new CampusFailure(404, "CAMPUS_BUILDING_NOT_FOUND", "The requested building does not exist."); }
    public static CampusFailure roomNotFound() { return new CampusFailure(404, "CAMPUS_ROOM_NOT_FOUND", "The requested room does not exist."); }
    public static CampusFailure placeNotFound() { return new CampusFailure(404, "CAMPUS_PLACE_NOT_FOUND", "The requested campus place does not exist."); }
    public static CampusFailure categoryNotFound() { return new CampusFailure(404, "CAMPUS_CATEGORY_NOT_FOUND", "The requested category does not exist."); }
    public static CampusFailure facilityNotFound() { return new CampusFailure(404, "CAMPUS_FACILITY_NOT_FOUND", "The requested facility does not exist."); }
    public static CampusFailure nodeNotFound() { return new CampusFailure(404, "CAMPUS_NODE_NOT_FOUND", "The requested navigation node does not exist."); }
    public static CampusFailure edgeNotFound() { return new CampusFailure(404, "CAMPUS_EDGE_NOT_FOUND", "The requested navigation edge does not exist."); }
    public static CampusFailure destinationNotFound() { return new CampusFailure(404, "CAMPUS_DESTINATION_NOT_FOUND", "The requested navigation destination does not exist."); }

    public static CampusFailure invalidSite() { return new CampusFailure(400, "CAMPUS_INVALID_SITE", "The campus does not exist, is deleted, or is inactive."); }
    public static CampusFailure invalidBuilding() { return new CampusFailure(400, "CAMPUS_INVALID_BUILDING", "The building does not exist, is deleted, or belongs to another campus."); }
    public static CampusFailure invalidCategory() { return new CampusFailure(400, "CAMPUS_INVALID_CATEGORY", "The category does not exist, is deleted, or is inactive."); }
    public static CampusFailure invalidFacility() { return new CampusFailure(400, "CAMPUS_INVALID_FACILITY", "A facility does not exist, is inactive, or is repeated."); }
    public static CampusFailure invalidFloor() { return new CampusFailure(400, "CAMPUS_INVALID_FLOOR", "The floor is not permitted by the building's floor configuration."); }
    public static CampusFailure invalidNode() { return new CampusFailure(400, "CAMPUS_INVALID_NODE", "The navigation node does not exist, is deleted, or belongs to another campus."); }
    public static CampusFailure invalidEdgeAccessibility(String message) { return new CampusFailure(400, "CAMPUS_INVALID_ACCESSIBILITY", message); }
    public static CampusFailure invalidDestination(String message) { return new CampusFailure(400, "CAMPUS_INVALID_DESTINATION", message); }
    public static CampusFailure invalidOpeningHours(String message) { return new CampusFailure(400, "CAMPUS_INVALID_OPENING_HOURS", message); }

    public static CampusFailure duplicate(String what) { return new CampusFailure(409, "CAMPUS_DUPLICATE_CODE", what + " already exists."); }
    public static CampusFailure primaryExists() { return new CampusFailure(409, "CAMPUS_PRIMARY_DESTINATION_EXISTS", "This location already has a primary destination."); }
    public static CampusFailure duplicateDestination() { return new CampusFailure(409, "CAMPUS_DUPLICATE_DESTINATION", "This location is already mapped to the navigation node."); }
    public static CampusFailure inUse(String message) { return new CampusFailure(409, "CAMPUS_DEPENDENCY_EXISTS", message); }

    public static CampusFailure destinationNotMapped() { return new CampusFailure(422, "CAMPUS_DESTINATION_NOT_MAPPED", "Navigation is not available for this location because no usable entrance is mapped."); }
    public static CampusFailure originOutside() { return new CampusFailure(422, "CAMPUS_ORIGIN_OUTSIDE_SUPPORTED_AREA", "Your location is too far from the campus walking network."); }
    public static CampusFailure noRoute() { return new CampusFailure(422, "CAMPUS_NO_AVAILABLE_ROUTE", "No walking route is available to this location."); }
    public static CampusFailure noAccessibleRoute() { return new CampusFailure(422, "CAMPUS_ACCESSIBLE_ROUTE_NOT_FOUND", "No wheelchair-accessible route is available to this location."); }
    public static CampusFailure underConstruction() { return new CampusFailure(422, "CAMPUS_ROUTE_UNDER_CONSTRUCTION", "The route to this location is currently under construction."); }
    public static CampusFailure graphInvalid() { return new CampusFailure(422, "CAMPUS_NAVIGATION_GRAPH_INVALID", "Navigation is unavailable because the campus map data is incomplete."); }
}
