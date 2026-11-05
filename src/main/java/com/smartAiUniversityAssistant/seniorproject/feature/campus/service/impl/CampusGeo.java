package com.smartAiUniversityAssistant.seniorproject.feature.campus.service.impl;

import java.math.*;

/** Geographic helpers. Straight-line distance is only ever used for snapping and labeled proximity, never as a walking edge. */
final class CampusGeo {
    private static final double EARTH_RADIUS_METERS = 6_371_000d;
    private CampusGeo() {}

    static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1), dLng = Math.toRadians(lng2 - lng1);
        double a = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLng / 2), 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(a)));
    }
    /** Initial bearing in whole degrees, 0 to 359. */
    static int bearingDegrees(double lat1, double lng1, double lat2, double lng2) {
        double phi1 = Math.toRadians(lat1), phi2 = Math.toRadians(lat2), dLng = Math.toRadians(lng2 - lng1);
        double y = Math.sin(dLng) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLng);
        return (int) Math.round((Math.toDegrees(Math.atan2(y, x)) + 360) % 360) % 360;
    }
    static String compass(int bearing) {
        return new String[] {"north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest"}[(int) Math.round(bearing / 45d) % 8];
    }
    static BigDecimal meters(double value) { return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP); }
    /** Whole minutes rounded up: any non-zero distance is at least one minute. */
    static int walkingMinutes(BigDecimal distanceMeters, double metersPerSecond) {
        return (int) Math.ceil(distanceMeters.doubleValue() / metersPerSecond / 60d);
    }
}
