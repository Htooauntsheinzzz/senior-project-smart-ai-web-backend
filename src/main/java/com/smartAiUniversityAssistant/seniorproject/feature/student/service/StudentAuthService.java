package com.smartAiUniversityAssistant.seniorproject.feature.student.service;

import com.smartAiUniversityAssistant.seniorproject.feature.student.dto.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedStudent;

public interface StudentAuthService {
    StudentRegisterResponse register(StudentRegisterRequest request);
    StudentLoginResponse login(StudentLoginRequest request);
    StudentRefreshResponse refresh(StudentRefreshRequest request);
    void logout(AuthenticatedStudent student, boolean allDevices);
    void changePassword(AuthenticatedStudent student, StudentChangePasswordRequest request);
    StudentMeResponse me(AuthenticatedStudent student);
}
