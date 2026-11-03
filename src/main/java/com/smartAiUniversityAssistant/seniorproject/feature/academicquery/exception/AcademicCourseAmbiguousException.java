package com.smartAiUniversityAssistant.seniorproject.feature.academicquery.exception;

import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.dto.AcademicCourseCandidate;
import java.util.List;

public class AcademicCourseAmbiguousException extends AcademicQueryFailure {
    private final List<AcademicCourseCandidate> candidates;
    public AcademicCourseAmbiguousException(List<AcademicCourseCandidate> candidates) {
        super(409, "ACADEMIC_COURSE_AMBIGUOUS", "The course identifier matches more than one course.");
        this.candidates = List.copyOf(candidates);
    }
    public List<AcademicCourseCandidate> candidates() { return candidates; }
}
