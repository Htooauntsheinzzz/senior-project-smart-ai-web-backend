package com.smartAiUniversityAssistant.seniorproject.feature.student.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.student.exception.*;
import org.springframework.dao.DataIntegrityViolationException;

/** Maps the student tables' unique constraints to stable error codes when a race reaches PostgreSQL. */
final class StudentConstraints {
    private StudentConstraints() {}

    static RuntimeException translate(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && violation.getConstraintName() != null) {
                switch (violation.getConstraintName()) {
                    case "uk_students_student_code": return new StudentCodeAlreadyExistsException();
                    case "uk_students_university_email": return new StudentEmailAlreadyExistsException();
                    case "uk_student_credentials_student_id": return new StudentAlreadyRegisteredException();
                    default: break;
                }
            }
        }
        return exception;
    }
}
