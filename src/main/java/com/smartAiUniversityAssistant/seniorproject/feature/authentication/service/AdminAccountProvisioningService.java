package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service;

import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;

public interface AdminAccountProvisioningService {
    ProvisionedAdminAccount provision(AuthenticatedUser actor, AdminAccountProvisioningCommand command);
}
