package com.smartAiUniversityAssistant.seniorproject.feature.department.service;

import java.util.Collection;
import java.util.Map;

public interface DepartmentStatisticsService {
    Map<Long, Long> countsByFaculty(Collection<Long> facultyIds);
    Map<Long, Long> programCountsByDepartment(Collection<Long> departmentIds);
    boolean hasNonDeletedPrograms(long departmentId);
    long total();
}
