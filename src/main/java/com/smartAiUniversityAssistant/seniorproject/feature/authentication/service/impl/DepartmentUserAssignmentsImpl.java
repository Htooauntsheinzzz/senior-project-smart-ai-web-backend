package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.authentication.repository.AppUserRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.DepartmentUserAssignments;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepartmentUserAssignmentsImpl implements DepartmentUserAssignments {
    private final AppUserRepository users;
    public DepartmentUserAssignmentsImpl(AppUserRepository users) { this.users = users; }
    @Override @Transactional(readOnly = true)
    public boolean hasNonDeletedUsers(long departmentId) {
        return users.existsByDepartmentIdAndDeletedFalse(departmentId);
    }
}
