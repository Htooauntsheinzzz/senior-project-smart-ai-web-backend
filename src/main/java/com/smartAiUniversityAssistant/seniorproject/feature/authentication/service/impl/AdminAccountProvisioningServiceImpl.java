package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.authentication.entity.*;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.exception.AdminAccountProvisioningFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.repository.*;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.*;
import com.smartAiUniversityAssistant.seniorproject.feature.department.service.DepartmentAssignmentService;
import com.smartAiUniversityAssistant.seniorproject.security.*;
import java.time.*;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAccountProvisioningServiceImpl implements AdminAccountProvisioningService {
    private static final Set<String> SUPPORTED_ROLES = Set.of("SUPER_ADMIN", "ADMIN", "ACADEMIC_ADMIN");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE", "LOCKED", "SUSPENDED");
    private final AppUserRepository users;
    private final AppRoleRepository roles;
    private final AppUserRoleRepository userRoles;
    private final AppUserCredentialsRepository credentials;
    private final CredentialPolicy credentialPolicy;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final DepartmentAssignmentService departmentAssignments;

    public AdminAccountProvisioningServiceImpl(AppUserRepository users, AppRoleRepository roles,
            AppUserRoleRepository userRoles, AppUserCredentialsRepository credentials,
            CredentialPolicy credentialPolicy, PasswordPolicy passwordPolicy,
            PasswordEncoder passwordEncoder, Clock clock, DepartmentAssignmentService departmentAssignments) {
        this.users = users;
        this.roles = roles;
        this.userRoles = userRoles;
        this.credentials = credentials;
        this.credentialPolicy = credentialPolicy;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.departmentAssignments = departmentAssignments;
    }

    @Override
    @Transactional(timeout = 15)
    public ProvisionedAdminAccount provision(AuthenticatedUser actor, AdminAccountProvisioningCommand command) {
        Instant instant = clock.instant();
        LocalDateTime now = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        AppUser actorUser = users.findByIdForUpdate(actor.userId())
                .orElseThrow(() -> new AccessDeniedException("Super Admin authority is required."));
        AppUserCredentials actorCredential = credentials.findByUserIdForUpdate(actor.userId())
                .orElseThrow(() -> new AccessDeniedException("Super Admin authority is required."));
        if (!credentialPolicy.eligible(actorUser, actorCredential, instant)
                || !passwordPolicy.usableHash(actorCredential.getPasswordHash())
                || !actor.credentialRevision().equals(TokenSupport.digest(actorCredential.getPasswordHash()))
                || userRoles.findActiveRoles(actor.userId()).stream().noneMatch(role -> role.code().equals("SUPER_ADMIN")))
            throw new AccessDeniedException("Super Admin authority is required.");
        if (actorCredential.isForcePasswordChange()) throw new PasswordChangeRequiredException();

        AppRole role = roles.findByIdForUpdate(command.roleId())
                .orElseThrow(() -> failure(404, "ROLE_NOT_FOUND", "The selected role does not exist."));
        if (!role.isActive()) throw failure(400, "ROLE_INACTIVE", "The selected role is inactive.");
        if (!SUPPORTED_ROLES.contains(role.getRoleCode()))
            throw failure(400, "ROLE_NOT_ASSIGNABLE", "The selected role cannot be assigned.");
        if (!STATUSES.contains(command.accountStatus()))
            throw failure(400, "VALIDATION_ERROR", "The request is invalid.");
        if (!passwordPolicy.validNewInput(command.temporaryPassword()))
            throw failure(400, "PASSWORD_POLICY_VIOLATION", "The temporary password violates the password policy.");

        boolean emailExists = users.existsByEmail(command.email());
        boolean employeeExists = users.existsByEmployeeId(command.employeeId());
        if (emailExists && employeeExists)
            throw failure(409, "USER_ALREADY_EXISTS", "The email and employee ID are already in use.");
        if (emailExists) throw duplicateEmail();
        if (employeeExists) throw duplicateEmployee();

        departmentAssignments.requireExistingForAssignment(command.departmentId());
        String hash = passwordEncoder.encode(command.temporaryPassword());
        try {
            var user = new AppUser();
            user.setEmployeeId(command.employeeId());
            user.setPhoneNumber(command.phoneNumber());
            user.setFirstName(command.firstName());
            user.setLastName(command.lastName());
            user.setEmail(command.email());
            user.setDepartmentId(command.departmentId());
            user.setAccountStatus(command.accountStatus());
            user.setDeleted(false);
            user.setCreatedBy(actor.userId());
            user.setCreatedAt(now);
            user = users.saveAndFlush(user);

            var assignment = new AppUserRole();
            assignment.setUser(user);
            assignment.setRole(role);
            assignment.setAssignedBy(actor.userId());
            assignment.setAssignedAt(now);
            userRoles.save(assignment);

            var credential = new AppUserCredentials();
            credential.setUser(user);
            credential.setPasswordHash(hash);
            credential.setForcePasswordChange(command.forcePasswordChange());
            credential.setFailedLoginAttempts(0);
            credential.setCreatedAt(now);
            credentials.save(credential);
            users.flush();

            return new ProvisionedAdminAccount(user.getId(), user.getEmployeeId(), user.getPhoneNumber(),
                    user.getFirstName(), user.getLastName(), user.getEmail(), user.getDepartmentId(),
                    user.getAccountStatus(), role.getId(), role.getRoleCode(), role.getRoleName(),
                    credential.isForcePasswordChange(), user.getCreatedBy(), instant);
        } catch (DataIntegrityViolationException exception) {
            throw translateConstraint(exception);
        }
    }

    private RuntimeException translateConstraint(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation) {
                if ("uk_app_users_email".equals(violation.getConstraintName())) return duplicateEmail();
                if ("uk_app_users_employee_id".equals(violation.getConstraintName())) return duplicateEmployee();
                if ("fk_app_users_department".equals(violation.getConstraintName()))
                    return failure(400, "DEPARTMENT_NOT_FOUND", "The selected department does not exist.");
                break;
            }
            cause = cause.getCause();
        }
        return exception;
    }

    private AdminAccountProvisioningFailure duplicateEmail() {
        return failure(409, "EMAIL_ALREADY_EXISTS", "The email is already in use.");
    }

    private AdminAccountProvisioningFailure duplicateEmployee() {
        return failure(409, "EMPLOYEE_ID_ALREADY_EXISTS", "The employee ID is already in use.");
    }

    private AdminAccountProvisioningFailure failure(int status, String code, String message) {
        return new AdminAccountProvisioningFailure(status, code, message);
    }
}
