package com.smartAiUniversityAssistant.seniorproject.feature.campus.service;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.dto.*;
import java.util.List;

public interface StudentCampusService {
    List<StudentCampusSiteResponse> sites();
    List<StudentCampusCategoryResponse> categories();
    StudentCampusLocationsResponse locations(Long siteId, String search, String group, Integer limit, String cursor);
    StudentCampusPlaceResponse place(long id);
    StudentCampusBuildingResponse building(long id);
    StudentCampusRoomsResponse buildingRooms(long id);
    StudentCampusRoomResponse room(long id);
    StudentCampusMapResponse map(Long siteId, boolean includeNavigationGraph);
}
