package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

/** Operational status counts are stored values. expectedOpenNow alone is time dependent, from published hours and exceptions. */
public record CampusPlaceStatisticsResponse(long totalPlaces, long activePlaces, long temporarilyClosedPlaces, long inactivePlaces,
        long popularPlaces, long visibleOnMobilePlaces, long expectedOpenNow, long activePlacesWithoutPublishedHours) {}
