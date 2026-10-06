package com.smartAiUniversityAssistant.seniorproject.feature.student.entity;

import com.smartAiUniversityAssistant.seniorproject.feature.department.entity.Department;
import com.smartAiUniversityAssistant.seniorproject.feature.faculty.entity.Faculty;
import com.smartAiUniversityAssistant.seniorproject.feature.program.entity.Program;
import com.smartAiUniversityAssistant.seniorproject.feature.semester.entity.Semester;
import jakarta.persistence.*;
import java.time.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "students") @Getter @Setter
public class Student {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "student_code", nullable = false, length = 50) private String studentCode;
    @Column(name = "university_email", nullable = false, length = 255) private String universityEmail;
    @Column(name = "first_name", nullable = false, length = 100) private String firstName;
    @Column(name = "last_name", nullable = false, length = 100) private String lastName;
    @Column(name = "phone_number", length = 30) private String phoneNumber;
    @Column(name = "date_of_birth") private LocalDate dateOfBirth;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "faculty_id") private Faculty faculty;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id") private Department department;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id") private Program program;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semester_id") private Semester semester;
    @Column(name = "academic_year") private Short academicYear;
    @Column(name = "enrollment_year") private Short enrollmentYear;
    @Column(name = "account_status", nullable = false, length = 30) private String accountStatus;
    @Column(name = "is_deleted", nullable = false) private boolean deleted;
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
}
