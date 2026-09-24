package com.smartAiUniversityAssistant.seniorproject.feature.department.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.department.exception.DepartmentFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.department.repository.DepartmentRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.department.service.DepartmentAssignmentService;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class DepartmentAssignmentServiceImpl implements DepartmentAssignmentService {
    private final DepartmentRepository departments;
    private final EntityManager entityManager;
    public DepartmentAssignmentServiceImpl(DepartmentRepository departments, EntityManager entityManager) {
        this.departments = departments;
        this.entityManager = entityManager;
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void requireExistingForAssignment(Long departmentId) {
        if (departmentId == null) return;
        // All assignment writers and department deletion use the same row lock.
        entityManager.createNativeQuery("SET LOCAL lock_timeout = '3s'").executeUpdate();
        var department = departments.findByIdForUpdate(departmentId).orElse(null);
        if (department == null || department.isDeleted())
            throw new DepartmentFailure(400, "DEPARTMENT_NOT_FOUND", "The selected department does not exist.");
    }
}
