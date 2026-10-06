package com.smartAiUniversityAssistant.seniorproject.feature.student.service;

import com.smartAiUniversityAssistant.seniorproject.feature.student.dto.StudentAuthCache;
import com.smartAiUniversityAssistant.seniorproject.feature.student.dto.StudentMeResponse;
import com.smartAiUniversityAssistant.seniorproject.feature.student.dto.StudentProfileUpdateRequest;
import java.util.Optional;

public interface StudentProfileService {
    /** Mobile profile, served from cache when present. */
    StudentMeResponse profile(long studentId);

    /** Authentication state for request authorization; empty when the student or credential is missing. */
    Optional<StudentAuthCache> authState(long studentId);

    /** Reads PostgreSQL directly and rejects anything but a non-deleted ACTIVE student with a credential. */
    StudentAuthCache requireRefreshEligible(long studentId);

    /** The student edits their own name, contact and academic placement. */
    StudentMeResponse updateProfile(long studentId, StudentProfileUpdateRequest request);
}
