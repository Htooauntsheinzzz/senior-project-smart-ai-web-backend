package com.smartAiUniversityAssistant.seniorproject.feature.coursesection.entity;

import com.smartAiUniversityAssistant.seniorproject.feature.course.entity.Course;
import com.smartAiUniversityAssistant.seniorproject.feature.lecture.entity.Lecture;
import com.smartAiUniversityAssistant.seniorproject.feature.semester.entity.Semester;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.entity.AcademicTerm;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "course_sections") @Getter @Setter
public class CourseSection {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false) private Course course;
    @Column(name = "section_number", nullable = false, length = 20) private String sectionNumber;
    @Column(name = "capacity", nullable = false) private Integer capacity;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lecture_id", nullable = false) private Lecture lecture;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "semester_id", nullable = false) private Semester semester;
    // Nullable until every section is reconciled with a verified term (spec file 42, section 10).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_term_id") private AcademicTerm academicTerm;
    @Column(name = "status", nullable = false, length = 30) private String status;
    @Column(name = "is_deleted", nullable = false) private boolean deleted;
    @Column(name = "created_by", nullable = false) private Long createdBy;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
}
