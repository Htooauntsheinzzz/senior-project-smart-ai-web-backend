package com.smartAiUniversityAssistant.seniorproject.feature.campus.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.entity.CampusNavDestination;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.CampusLocationType;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.repository.CampusNavDestinationRepository;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * Turns a building or place into its mapped entrances. A place without its own mapping may use its building's verified
 * entrance. Coordinates exist only through these mappings; a location with none stays unmapped and cannot be navigated to.
 */
@Component
class CampusDestinationResolver {
    /** statusActive: the location itself is ACTIVE. buildingId/buildingActive: the place's building, or the building itself. */
    record Location(CampusLocationType type, long id, long siteId, boolean statusActive, Long buildingId, boolean buildingActive) {}
    record Entrance(long nodeId, boolean accessible, boolean primary, BigDecimal latitude, BigDecimal longitude) {}
    record Resolved(BigDecimal latitude, BigDecimal longitude, boolean canNavigate, List<Entrance> entrances) {
        static final Resolved NONE = new Resolved(null, null, false, List.of());
    }
    record Key(CampusLocationType type, long id) {}

    private final CampusNavDestinationRepository destinations;
    CampusDestinationResolver(CampusNavDestinationRepository destinations) { this.destinations = destinations; }

    Map<Key, Resolved> resolve(Collection<Location> locations) {
        if (locations.isEmpty()) return Map.of();
        var buildingIds = new HashSet<Long>();
        var placeIds = new HashSet<Long>();
        for (Location location : locations) {
            if (location.buildingId() != null) buildingIds.add(location.buildingId());
            if (location.type() == CampusLocationType.PLACE) placeIds.add(location.id());
        }
        // An empty IN list is avoided with an id that cannot exist.
        var rows = destinations.usableFor(buildingIds.isEmpty() ? List.of(-1L) : buildingIds, placeIds.isEmpty() ? List.of(-1L) : placeIds);
        var byBuilding = new HashMap<Long, List<CampusNavDestination>>();
        var byPlace = new HashMap<Long, List<CampusNavDestination>>();
        for (var row : rows) {
            if (row.getBuilding() != null) byBuilding.computeIfAbsent(row.getBuilding().getId(), k -> new ArrayList<>()).add(row);
            else byPlace.computeIfAbsent(row.getPlace().getId(), k -> new ArrayList<>()).add(row);
        }
        var resolved = new HashMap<Key, Resolved>();
        for (Location location : locations) {
            var own = location.type() == CampusLocationType.PLACE ? byPlace.getOrDefault(location.id(), List.of()) : List.<CampusNavDestination>of();
            var chosen = !own.isEmpty() || location.buildingId() == null ? own : byBuilding.getOrDefault(location.buildingId(), List.of());
            resolved.put(new Key(location.type(), location.id()), resolved(location, chosen));
        }
        return resolved;
    }

    Resolved resolveOne(Location location) {
        return resolve(List.of(location)).getOrDefault(new Key(location.type(), location.id()), Resolved.NONE);
    }

    private Resolved resolved(Location location, List<CampusNavDestination> rows) {
        var entrances = rows.stream().filter(row -> row.getNode().getSite().getId().equals(location.siteId()))
                .map(row -> new Entrance(row.getNode().getId(), row.isAccessibleEntrance() && row.getNode().isAccessible(), row.isPrimary(),
                        row.getNode().getLatitude(), row.getNode().getLongitude())).toList();
        if (entrances.isEmpty()) return Resolved.NONE;
        Entrance marker = entrances.stream().filter(Entrance::primary).findFirst().orElse(entrances.get(0));
        // A location under renovation or closed, or inside a building that is, is not offered as a destination.
        boolean navigable = location.statusActive() && (location.buildingId() == null || location.buildingActive());
        return new Resolved(marker.latitude(), marker.longitude(), navigable, entrances);
    }
}
