package com.smartAiUniversityAssistant.seniorproject.feature.student.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.department.entity.Department;
import com.smartAiUniversityAssistant.seniorproject.feature.department.repository.DepartmentRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.faculty.entity.Faculty;
import com.smartAiUniversityAssistant.seniorproject.feature.faculty.repository.FacultyRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.program.entity.Program;
import com.smartAiUniversityAssistant.seniorproject.feature.program.repository.ProgramRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.semester.entity.Semester;
import com.smartAiUniversityAssistant.seniorproject.feature.semester.repository.SemesterRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.student.entity.Student;
import com.smartAiUniversityAssistant.seniorproject.feature.student.exception.InvalidStudentAcademicRelationshipException;
import com.smartAiUniversityAssistant.seniorproject.feature.student.service.StudentAcademicValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentAcademicValidatorImpl implements StudentAcademicValidator {
    private final FacultyRepository faculties;
    private final DepartmentRepository departments;
    private final ProgramRepository programs;
    private final SemesterRepository semesters;

    public StudentAcademicValidatorImpl(FacultyRepository faculties, DepartmentRepository departments,
            ProgramRepository programs, SemesterRepository semesters) {
        this.faculties = faculties;
        this.departments = departments;
        this.programs = programs;
        this.semesters = semesters;
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public AcademicPlacement validate(Long facultyId, Long departmentId, Long programId, Long semesterId,
            Student current) {
        Faculty faculty = faculties.findByIdAndDeletedFalse(facultyId)
                .orElseThrow(() -> invalid("The selected faculty does not exist."));
        if (!faculty.isActive() && changed(current == null || current.getFaculty() == null ? null : current.getFaculty().getId(), facultyId))
            throw invalid("The selected faculty is inactive.");
        Department department = departments.findByIdAndDeletedFalse(departmentId)
                .orElseThrow(() -> invalid("The selected department does not exist."));
        if (!department.isActive() && changed(current == null || current.getDepartment() == null ? null : current.getDepartment().getId(), departmentId))
            throw invalid("The selected department is inactive.");
        if (!department.getFaculty().getId().equals(faculty.getId()))
            throw invalid("The selected department does not belong to the selected faculty.");
        Program program = programs.findByIdAndDeletedFalse(programId)
                .orElseThrow(() -> invalid("The selected program does not exist."));
        if (!program.isActive() && changed(current == null || current.getProgram() == null ? null : current.getProgram().getId(), programId))
            throw invalid("The selected program is inactive.");
        if (!program.getDepartment().getId().equals(department.getId()))
            throw invalid("The selected program does not belong to the selected department.");
        Semester semester = null;
        if (semesterId != null) {
            semester = semesters.findByIdAndDeletedFalse(semesterId)
                    .orElseThrow(() -> invalid("The selected semester does not exist."));
        }
        return new AcademicPlacement(faculty, department, program, semester);
    }

    private boolean changed(Long currentId, Long requestedId) { return !requestedId.equals(currentId); }

    private InvalidStudentAcademicRelationshipException invalid(String message) {
        return new InvalidStudentAcademicRelationshipException(message);
    }
}
