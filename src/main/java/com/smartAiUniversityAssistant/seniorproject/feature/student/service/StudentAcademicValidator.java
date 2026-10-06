package com.smartAiUniversityAssistant.seniorproject.feature.student.service;

import com.smartAiUniversityAssistant.seniorproject.feature.department.entity.Department;
import com.smartAiUniversityAssistant.seniorproject.feature.faculty.entity.Faculty;
import com.smartAiUniversityAssistant.seniorproject.feature.program.entity.Program;
import com.smartAiUniversityAssistant.seniorproject.feature.semester.entity.Semester;
import com.smartAiUniversityAssistant.seniorproject.feature.student.entity.Student;

public interface StudentAcademicValidator {
    record AcademicPlacement(Faculty faculty, Department department, Program program, Semester semester) {}

    /**
     * Resolves and validates the faculty → department → program hierarchy and the optional semester.
     * {@code current} is the student being updated (null on create); unchanged references may stay
     * inactive so historical placements can still be edited.
     */
    AcademicPlacement validate(Long facultyId, Long departmentId, Long programId, Long semesterId, Student current);
}
