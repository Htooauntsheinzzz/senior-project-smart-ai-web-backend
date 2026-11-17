package com.smartAiUniversityAssistant.seniorproject.feature.user.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.*;
import com.smartAiUniversityAssistant.seniorproject.feature.user.dto.AdminUserResponse;
import com.smartAiUniversityAssistant.seniorproject.feature.user.dto.CreateAdminUserRequest;
import com.smartAiUniversityAssistant.seniorproject.feature.user.dto.request.*;
import com.smartAiUniversityAssistant.seniorproject.feature.user.dto.response.*;
import com.smartAiUniversityAssistant.seniorproject.feature.user.exception.AdminUserQueryValidationException;
import com.smartAiUniversityAssistant.seniorproject.feature.user.mapper.AdminUserMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.user.service.AdminUserService;
import com.smartAiUniversityAssistant.seniorproject.security.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class AdminUserServiceImpl implements AdminUserService {
    private final AdminAccountProvisioningService provisioning;
    private final AdminAccountManagementService management;
    private final AdminUserMapper mapper;
    private final AdminMailService mail;

    public AdminUserServiceImpl(AdminAccountProvisioningService provisioning,
            AdminAccountManagementService management, AdminUserMapper mapper, AdminMailService mail) {
        this.provisioning = provisioning;
        this.management = management;
        this.mapper = mapper;
        this.mail = mail;
    }

    @Override
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public AdminUserResponse create(AuthenticatedUser actor, CreateAdminUserRequest request) {
        requireFullSession(actor);
        var command = new AdminAccountProvisioningCommand(request.employeeId(), request.phoneNumber(),
                request.firstName(), request.lastName(), request.email(), request.departmentId(),
                request.accountStatus(), request.roleId(), request.temporaryPassword(),
                request.forcePasswordChange());
        return mapper.response(provisioning.provision(actor, command));
    }

    @Override
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public AdminUserPageResponse list(AuthenticatedUser actor, AdminUserListQuery query) {
        requireFullSession(actor);
        AdminAccountListQuery parsed = query.parse();
        return mapper.page(management.page(parsed), parsed);
    }

    @Override
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public AdminUserDetailResponse detail(AuthenticatedUser actor, long id) {
        requireFullSession(actor);
        requirePositiveId(id);
        return mapper.detail(management.detail(id));
    }

    @Override
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public AdminUserDetailResponse update(AuthenticatedUser actor, long id, UpdateAdminUserRequest request) {
        requireFullSession(actor);
        requirePositiveId(id);
        var command = new UpdateAdminAccountCommand(request.employeeId(), request.phoneNumber(),
                request.firstName(), request.lastName(), request.email(), request.departmentId(),
                request.accountStatus(), request.roleId());
        return mapper.detail(management.update(actor, id, command));
    }

    @Override
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public AdminUserDetailResponse updateStatus(AuthenticatedUser actor, long id,
            UpdateAdminUserStatusRequest request) {
        requireFullSession(actor);
        requirePositiveId(id);
        return mapper.detail(management.updateStatus(actor, id, request.accountStatus()));
    }

    @Override
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public AdminUserDetailResponse updateRole(AuthenticatedUser actor, long id, UpdateAdminUserRoleRequest request) {
        requireFullSession(actor);
        requirePositiveId(id);
        return mapper.detail(management.updateRole(actor, id, request.roleId()));
    }

    @Override
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public void delete(AuthenticatedUser actor, long id) {
        requireFullSession(actor);
        requirePositiveId(id);
        management.delete(actor, id);
    }

    // Spec file 62. No transaction here: the decision is committed first, then the mail is sent, so a mail server
    // problem never undoes a decision and no row lock is held while a mail is sent.
    @Override
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public AdminUserDetailResponse approveRegistration(AuthenticatedUser actor, long id) {
        requireFullSession(actor);
        requirePositiveId(id);
        AdminAccountData approved = management.approveRegistration(actor, id);
        mail.sendApproved(approved.email(), approved.firstName());
        return mapper.detail(approved);
    }

    @Override
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public void rejectRegistration(AuthenticatedUser actor, long id) {
        requireFullSession(actor);
        requirePositiveId(id);
        var rejected = management.rejectRegistration(actor, id);
        // Nobody proved an unverified address, so no mail goes to it.
        if (rejected.emailProven()) mail.sendRejected(rejected.email(), rejected.firstName());
    }

    private void requireFullSession(AuthenticatedUser actor) {
        if (actor.forcePasswordChange()) throw new PasswordChangeRequiredException();
    }

    private void requirePositiveId(long id) {
        if (id <= 0) throw new AdminUserQueryValidationException("The user ID must be a positive integer.");
    }
}
