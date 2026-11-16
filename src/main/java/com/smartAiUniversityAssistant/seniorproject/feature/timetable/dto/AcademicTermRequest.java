package com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.timetable.enums.AcademicTermStatus;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

/** Create and full-update body. An omitted status means PLANNED on create and "keep" on update. */
public record AcademicTermRequest(@NotNull @Positive Long semesterId, @NotBlank @Size(max = 20) String academicYearCode,
        @NotBlank @Size(max = 30) String academicYearLabel, @NotNull LocalDate startDate, @NotNull LocalDate endDate,
        AcademicTermStatus status) {
    public AcademicTermRequest {
        academicYearCode = academicYearCode == null ? null : academicYearCode.trim();
        academicYearLabel = academicYearLabel == null ? null : academicYearLabel.trim();
    }
}
