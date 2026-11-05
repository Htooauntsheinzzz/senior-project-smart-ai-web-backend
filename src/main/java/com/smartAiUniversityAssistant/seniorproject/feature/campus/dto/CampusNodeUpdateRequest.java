package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CampusNodeUpdateRequest(
        @NotBlank @Size(max = 50) String nodeCode,
        @NotBlank @Size(max = 200) String nodeName,
        @NotNull CampusNodeType nodeType,
        BigDecimal latitude,
        BigDecimal longitude,
        Boolean isAccessible,
        Boolean isActive) {}
