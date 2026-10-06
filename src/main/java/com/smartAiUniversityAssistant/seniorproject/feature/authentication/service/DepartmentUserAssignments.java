package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service;

public interface DepartmentUserAssignments {
    boolean hasNonDeletedUsers(long departmentId);
}
