package com.smartAiUniversityAssistant.seniorproject.feature.user.service;

import com.smartAiUniversityAssistant.seniorproject.feature.user.dto.AdminUserResponse;
import com.smartAiUniversityAssistant.seniorproject.feature.user.dto.CreateAdminUserRequest;
import com.smartAiUniversityAssistant.seniorproject.feature.user.dto.request.*;
import com.smartAiUniversityAssistant.seniorproject.feature.user.dto.response.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;

public interface AdminUserService {

    AdminUserResponse create(AuthenticatedUser actor, CreateAdminUserRequest request);

    AdminUserPageResponse list(AuthenticatedUser actor, AdminUserListQuery query);

    AdminUserDetailResponse detail(AuthenticatedUser actor, long id);

    AdminUserDetailResponse update(AuthenticatedUser actor, long id, UpdateAdminUserRequest request);

    AdminUserDetailResponse updateStatus(AuthenticatedUser actor, long id, UpdateAdminUserStatusRequest request);

    AdminUserDetailResponse updateRole(AuthenticatedUser actor, long id, UpdateAdminUserRoleRequest request);

    void delete(AuthenticatedUser actor, long id);
}
