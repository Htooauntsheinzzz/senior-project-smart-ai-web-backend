package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.*;
import java.math.BigDecimal;
import java.time.*;

public record CampusNodeResponse(Long id, Long siteId, String nodeCode, String nodeName, CampusNodeType nodeType, BigDecimal latitude,
        BigDecimal longitude, boolean isAccessible, boolean isActive, Long createdBy, Instant createdAt, Long updatedBy, Instant updatedAt) {}
