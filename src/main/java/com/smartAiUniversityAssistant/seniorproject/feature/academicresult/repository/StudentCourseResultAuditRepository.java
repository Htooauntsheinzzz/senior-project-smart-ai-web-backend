package com.smartAiUniversityAssistant.seniorproject.feature.academicresult.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.academicresult.entity.StudentCourseResultAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentCourseResultAuditRepository extends JpaRepository<StudentCourseResultAudit, Long> {
}
