package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service;

import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;

public interface AdminAccountManagementService {
    AdminAccountPageData page(AdminAccountListQuery query);
    AdminAccountData detail(long targetId);
    AdminAccountData update(AuthenticatedUser actor, long targetId, UpdateAdminAccountCommand command);
    AdminAccountData updateStatus(AuthenticatedUser actor, long targetId, String accountStatus);
    AdminAccountData updateRole(AuthenticatedUser actor, long targetId, Long roleId);
    void delete(AuthenticatedUser actor, long targetId);
}
