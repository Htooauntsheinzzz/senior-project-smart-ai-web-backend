package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.*;

/** The expected state from published data. It is never a verified real-time observation. */
public record CampusOpeningStatus(Boolean expectedOpen, String basis, boolean verifiedRealTime,
        @JsonFormat(pattern = "HH:mm") LocalTime opensAt, @JsonFormat(pattern = "HH:mm") LocalTime closesAt, String reason) {}
