package com.smartAiUniversityAssistant.seniorproject.feature.course.entity;

import com.smartAiUniversityAssistant.seniorproject.feature.department.entity.Department;
import com.smartAiUniversityAssistant.seniorproject.feature.faculty.entity.Faculty;
import com.smartAiUniversityAssistant.seniorproject.feature.program.entity.Program;
import com.smartAiUniversityAssistant.seniorproject.feature.semester.entity.Semester;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "courses") @Getter @Setter
public class Course {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "course_code", nullable = false, length = 50) private String courseCode;
    @Column(name = "course_name", nullable = false, length = 255) private String courseName;
    @Column(name = "credit_hours", nullable = false) private Integer creditHours;
    @Column(name = "description", length = 1000) private String description;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "faculty_id", nullable = false) private Faculty faculty;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false) private Department department;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id") private Program program;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semester_id") private Semester semester;
    @Column(name = "recommended_academic_year") private Short recommendedAcademicYear;
    @Column(name = "prerequisite_courses", length = 1000) private String prerequisiteCourses;
    @Column(name = "course_type", nullable = false) private Short courseType;
    @Column(name = "maximum_students_per_section") private Integer maximumStudentsPerSection;
    @Column(name = "is_active", nullable = false) private boolean active;
    @Column(name = "is_deleted", nullable = false) private boolean deleted;
    @Column(name = "created_by", nullable = false) private Long createdBy;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
}
