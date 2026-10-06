package com.smartAiUniversityAssistant.seniorproject.feature.department.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.department.repository.DepartmentRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.department.service.DepartmentStatisticsService;
import com.smartAiUniversityAssistant.seniorproject.feature.program.repository.ProgramRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DepartmentStatisticsServiceImpl implements DepartmentStatisticsService {
    private final DepartmentRepository departments;
    private final ProgramRepository programs;
    public DepartmentStatisticsServiceImpl(DepartmentRepository departments, ProgramRepository programs) {
        this.departments = departments;
        this.programs = programs;
    }
    public Map<Long, Long> countsByFaculty(Collection<Long> facultyIds) {
        if (facultyIds.isEmpty()) return Map.of();
        var result = new HashMap<Long, Long>();
        departments.countForFaculties(facultyIds).forEach(row -> result.put(row.getFacultyId(), row.getTotal()));
        return Map.copyOf(result);
    }
    public Map<Long, Long> programCountsByDepartment(Collection<Long> departmentIds) {
        if (departmentIds.isEmpty()) return Map.of();
        var result = new HashMap<Long, Long>();
        programs.countForDepartments(departmentIds).forEach(row -> result.put(row.getDepartmentId(), row.getTotal()));
        return Map.copyOf(result);
    }
    public boolean hasNonDeletedPrograms(long departmentId) {
        return programs.existsByDepartmentIdAndDeletedFalse(departmentId);
    }
    public long total() { return departments.countByDeletedFalse(); }
}
