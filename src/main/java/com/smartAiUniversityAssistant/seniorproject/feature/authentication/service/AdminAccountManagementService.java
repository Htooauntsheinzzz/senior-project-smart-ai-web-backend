package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service;

import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;

public interface AdminAccountManagementService {
    AdminAccountPageData page(AdminAccountListQuery query);
    AdminAccountData detail(long targetId);
    AdminAccountData update(AuthenticatedUser actor, long targetId, UpdateAdminAccountCommand command);
    AdminAccountData updateStatus(AuthenticatedUser actor, long targetId, String accountStatus);
    AdminAccountData updateRole(AuthenticatedUser actor, long targetId, Long roleId);
    void delete(AuthenticatedUser actor, long targetId);

    /** Who a registration decision is about, for the mail that follows it (spec file 62). */
    record RegistrationContact(String email, String firstName, boolean emailProven) {
        @Override public String toString() { return "RegistrationContact[REDACTED]"; }
    }

    /** Spec file 62: a waiting registration becomes ACTIVE. 409 REGISTRATION_NOT_PENDING for any other account. */
    AdminAccountData approveRegistration(AuthenticatedUser actor, long targetId);

    /** Spec file 62: a waiting or unverified registration is deleted like {@link #delete}. */
    RegistrationContact rejectRegistration(AuthenticatedUser actor, long targetId);
}
