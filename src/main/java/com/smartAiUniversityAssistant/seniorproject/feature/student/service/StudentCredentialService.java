package com.smartAiUniversityAssistant.seniorproject.feature.student.service;

import com.smartAiUniversityAssistant.seniorproject.feature.student.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.student.entity.Student;
import com.smartAiUniversityAssistant.seniorproject.feature.student.entity.StudentCredential;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;

public interface StudentCredentialService {
    record LoginOutcome(long studentId, String studentCode, boolean forcePasswordChange) {}

    /** Checks confirmation and password policy, then returns the BCrypt hash. */
    String encodeNewPassword(String password, String confirmPassword);

    /** Inserts the credential row inside the caller's transaction. */
    StudentCredential create(Student student, String passwordHash, boolean forcePasswordChange);

    void resetPassword(AuthenticatedUser actor, long studentId, StudentPasswordResetRequest request);

    /** Locks the PENDING profile, inserts the credential and moves it to ACTIVE atomically. */
    Student completeRegistration(StudentRegisterRequest request, String passwordHash);

    /** PostgreSQL authentication; failed-attempt counters commit even when the login is rejected. */
    LoginOutcome verifyLogin(String universityEmail, String password);

    void changePassword(long studentId, StudentChangePasswordRequest request);
}
