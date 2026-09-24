package com.smartAiUniversityAssistant.seniorproject.feature.department.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.department.repository.DepartmentRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.department.service.DepartmentStatisticsService;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DepartmentStatisticsServiceImpl implements DepartmentStatisticsService {
    private final DepartmentRepository departments;
    public DepartmentStatisticsServiceImpl(DepartmentRepository departments) { this.departments = departments; }
    public Map<Long, Long> countsByFaculty(Collection<Long> facultyIds) {
        if (facultyIds.isEmpty()) return Map.of();
        var result = new HashMap<Long, Long>();
        departments.countForFaculties(facultyIds).forEach(row -> result.put(row.getFacultyId(), row.getTotal()));
        return Map.copyOf(result);
    }
    public long total() { return departments.countByDeletedFalse(); }
}
