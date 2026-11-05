package com.smartAiUniversityAssistant.seniorproject.feature.campus.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.dto.CampusRouteResponse;
import java.math.BigDecimal;
import java.util.*;

/**
 * Turn guidance built only from measured geometry. If any walked edge has no intermediate points the bearings would be
 * guesses, so the result is empty and the client shows the route overview instead (spec section 54).
 */
final class CampusRouteInstructions {
    private CampusRouteInstructions() {}

    static boolean available(List<CampusRouteGraph.Step> steps) {
        return !steps.isEmpty() && steps.stream().allMatch(CampusRouteGraph.Step::hasMeasuredGeometry);
    }

    static List<CampusRouteResponse.Instruction> build(List<CampusRouteGraph.Step> steps, String destinationName) {
        if (!available(steps)) return List.of();
        var instructions = new ArrayList<CampusRouteResponse.Instruction>();
        Integer previousBearing = null;
        for (var step : steps) {
            var line = step.line();
            int bearing = bearing(line.get(0), line.get(1));
            String direction = CampusGeo.compass(bearing);
            String text = previousBearing == null
                    ? "Head " + direction + " toward " + step.to().name()
                    : turn(previousBearing, bearing) + " toward " + step.to().name();
            instructions.add(new CampusRouteResponse.Instruction(text, step.edge().distanceMeters(), bearing, direction, step.edge().code()));
            previousBearing = bearing(line.get(line.size() - 2), line.get(line.size() - 1));
        }
        instructions.add(new CampusRouteResponse.Instruction("Arrive at " + destinationName, BigDecimal.ZERO.setScale(2), null, null, null));
        return instructions;
    }

    private static int bearing(CampusRouteGraph.Point from, CampusRouteGraph.Point to) {
        return CampusGeo.bearingDegrees(from.latitude(), from.longitude(), to.latitude(), to.longitude());
    }

    static String turn(int fromBearing, int toBearing) {
        int delta = ((toBearing - fromBearing) % 360 + 540) % 360 - 180;
        int size = Math.abs(delta);
        if (size < 30) return "Continue straight";
        if (size > 150) return "Turn around";
        return delta > 0 ? "Turn right" : "Turn left";
    }
}
