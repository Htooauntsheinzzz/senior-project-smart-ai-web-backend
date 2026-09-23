package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.authentication.entity.*;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.exception.AdminAccountManagementFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.repository.*;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.*;
import com.smartAiUniversityAssistant.seniorproject.security.*;
import jakarta.persistence.criteria.*;
import java.time.*;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAccountManagementServiceImpl implements AdminAccountManagementService {
    private static final Set<String> SUPPORTED_ROLES = Set.of("SUPER_ADMIN", "ADMIN", "ACADEMIC_ADMIN");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE", "LOCKED", "SUSPENDED");
    private final AppUserRepository users;
    private final AppRoleRepository roles;
    private final AppUserRoleRepository userRoles;
    private final AppUserCredentialsRepository credentials;
    private final CredentialPolicy credentialPolicy;
    private final PasswordPolicy passwordPolicy;
    private final Clock clock;

    public AdminAccountManagementServiceImpl(AppUserRepository users, AppRoleRepository roles,
            AppUserRoleRepository userRoles, AppUserCredentialsRepository credentials,
            CredentialPolicy credentialPolicy, PasswordPolicy passwordPolicy, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.userRoles = userRoles;
        this.credentials = credentials;
        this.credentialPolicy = credentialPolicy;
        this.passwordPolicy = passwordPolicy;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true, timeout = 15)
    public AdminAccountPageData page(AdminAccountListQuery query) {
        Page<AppUser> page = users.findAll(specification(query), pageRequest(query));
        List<Long> ids = page.getContent().stream().map(AppUser::getId).toList();
        Map<Long, List<AdminRoleAssignmentData>> assignments = assignmentsByUser(ids);
        List<AdminAccountData> content = page.getContent().stream()
                .map(user -> data(user, assignments.getOrDefault(user.getId(), List.of()))).toList();
        return new AdminAccountPageData(content, page.getTotalElements(), page.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true, timeout = 15)
    public AdminAccountData detail(long targetId) {
        AppUser user = users.findByIdAndDeletedFalse(targetId).orElseThrow(this::userNotFound);
        return data(user, assignmentsByUser(List.of(user.getId())).getOrDefault(user.getId(), List.of()));
    }

    @Override
    @Transactional(timeout = 15)
    public AdminAccountData update(AuthenticatedUser actor, long targetId, UpdateAdminAccountCommand command) {
        LocalDateTime now = utcNow();
        validateActor(actor, clock.instant());
        AppUser target = lockNonDeletedTarget(targetId);
        AppRole role = lockSupportedRole(command.roleId());
        if (!STATUSES.contains(command.accountStatus()))
            throw failure(400, "VALIDATION_ERROR", "The request is invalid.");
        boolean emailExists = users.existsByEmailAndIdNot(command.email(), targetId);
        boolean employeeExists = users.existsByEmployeeIdAndIdNot(command.employeeId(), targetId);
        if (emailExists && employeeExists)
            throw failure(409, "USER_ALREADY_EXISTS", "The email and employee ID are already in use.");
        if (emailExists) throw duplicateEmail();
        if (employeeExists) throw duplicateEmployee();

        List<AppUserRole> assignments = userRoles.findByUserId(targetId);
        boolean assignmentChanged = !isExactSingleAssignment(assignments, role.getId());
        boolean changed = profileChanged(target, command) || !target.getAccountStatus().equals(command.accountStatus())
                || !Objects.equals(target.getDepartmentId(), command.departmentId()) || assignmentChanged;
        if (actor.userId() == targetId
                && (!target.getAccountStatus().equals(command.accountStatus()) || assignmentChanged))
            throw selfChange();
        if (changed) {
            target.setEmployeeId(command.employeeId());
            target.setFirstName(command.firstName());
            target.setLastName(command.lastName());
            target.setEmail(command.email());
            target.setPhoneNumber(command.phoneNumber());
            target.setDepartmentId(command.departmentId());
            target.setAccountStatus(command.accountStatus());
            replaceAssignments(target, assignments, role, actor.userId(), now);
            audit(target, actor.userId(), now);
            try {
                users.flush();
            } catch (DataIntegrityViolationException exception) {
                throw translateConstraint(exception);
            }
        }
        return data(target, mapAssignments(userRoles.findByUserId(targetId)));
    }

    @Override
    @Transactional(timeout = 15)
    public AdminAccountData updateStatus(AuthenticatedUser actor, long targetId, String accountStatus) {
        LocalDateTime now = utcNow();
        validateActor(actor, clock.instant());
        if (!STATUSES.contains(accountStatus)) throw failure(400, "VALIDATION_ERROR", "The request is invalid.");
        AppUser target = lockNonDeletedTarget(targetId);
        if (!target.getAccountStatus().equals(accountStatus)) {
            if (actor.userId() == targetId) throw selfChange();
            target.setAccountStatus(accountStatus);
            audit(target, actor.userId(), now);
            users.flush();
        }
        return data(target, mapAssignments(userRoles.findByUserId(targetId)));
    }

    @Override
    @Transactional(timeout = 15)
    public AdminAccountData updateRole(AuthenticatedUser actor, long targetId, Long roleId) {
        LocalDateTime now = utcNow();
        validateActor(actor, clock.instant());
        AppUser target = lockNonDeletedTarget(targetId);
        AppRole role = lockSupportedRole(roleId);
        List<AppUserRole> assignments = userRoles.findByUserId(targetId);
        if (!isExactSingleAssignment(assignments, role.getId())) {
            if (actor.userId() == targetId) throw selfChange();
            replaceAssignments(target, assignments, role, actor.userId(), now);
            audit(target, actor.userId(), now);
            users.flush();
        }
        return data(target, mapAssignments(userRoles.findByUserId(targetId)));
    }

    @Override
    @Transactional(timeout = 15)
    public void delete(AuthenticatedUser actor, long targetId) {
        LocalDateTime now = utcNow();
        validateActor(actor, clock.instant());
        if (actor.userId() == targetId) throw selfChange();
        AppUser target = users.findByIdForUpdate(targetId).orElseThrow(this::userNotFound);
        if (target.isDeleted()) return;
        target.setDeleted(true);
        audit(target, actor.userId(), now);
        users.flush();
    }

    private void validateActor(AuthenticatedUser actor, Instant instant) {
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
    }

    private AppUser lockNonDeletedTarget(long targetId) {
        AppUser target = users.findByIdForUpdate(targetId).orElseThrow(this::userNotFound);
        if (target.isDeleted()) throw userNotFound();
        return target;
    }

    private AppRole lockSupportedRole(Long roleId) {
        AppRole role = roles.findByIdForUpdate(roleId)
                .orElseThrow(() -> failure(400, "INVALID_ROLE", "The selected role cannot be assigned."));
        if (!role.isActive() || !SUPPORTED_ROLES.contains(role.getRoleCode()))
            throw failure(400, "INVALID_ROLE", "The selected role cannot be assigned.");
        return role;
    }

    private boolean isExactSingleAssignment(List<AppUserRole> assignments, Long roleId) {
        return assignments.size() == 1 && assignments.get(0).getRole().getId().equals(roleId);
    }

    private void replaceAssignments(AppUser target, List<AppUserRole> assignments, AppRole role,
            long actorId, LocalDateTime now) {
        AppUserRole keep = assignments.stream().filter(a -> a.getRole().getId().equals(role.getId()))
                .findFirst().orElse(null);
        List<AppUserRole> remove = new ArrayList<>(assignments);
        if (keep != null) remove.remove(keep);
        userRoles.deleteAll(remove);
        if (keep == null) {
            var assignment = new AppUserRole();
            assignment.setUser(target);
            assignment.setRole(role);
            assignment.setAssignedBy(actorId);
            assignment.setAssignedAt(now);
            userRoles.save(assignment);
        }
    }

    private boolean profileChanged(AppUser target, UpdateAdminAccountCommand command) {
        return !target.getEmployeeId().equals(command.employeeId())
                || !target.getFirstName().equals(command.firstName())
                || !target.getLastName().equals(command.lastName())
                || !target.getEmail().equals(command.email())
                || !Objects.equals(target.getPhoneNumber(), command.phoneNumber());
    }

    private void audit(AppUser target, long actorId, LocalDateTime now) {
        target.setUpdatedBy(actorId);
        target.setUpdatedAt(now);
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private Specification<AppUser> specification(AdminAccountListQuery query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("deleted")));
            if (query.search() != null) {
                String pattern = "%" + escapeLike(query.search().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("employeeId")), pattern, '\\'),
                        cb.like(cb.lower(root.get("firstName")), pattern, '\\'),
                        cb.like(cb.lower(root.get("lastName")), pattern, '\\'),
                        cb.like(cb.lower(root.get("email")), pattern, '\\')));
            }
            if (query.accountStatus() != null)
                predicates.add(cb.equal(root.get("accountStatus"), query.accountStatus()));
            if (query.departmentUnassigned()) predicates.add(cb.isNull(root.get("departmentId")));
            else if (query.departmentId() != null)
                predicates.add(cb.equal(root.get("departmentId"), query.departmentId()));
            if (query.roleId() != null) {
                Subquery<Integer> exists = criteriaQuery.subquery(Integer.class);
                Root<AppUserRole> assignment = exists.from(AppUserRole.class);
                exists.select(cb.literal(1)).where(
                        cb.equal(assignment.get("user").get("id"), root.get("id")),
                        cb.equal(assignment.get("role").get("id"), query.roleId()));
                predicates.add(cb.exists(exists));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private PageRequest pageRequest(AdminAccountListQuery query) {
        List<Sort.Order> orders = new ArrayList<>();
        for (AdminAccountListQuery.Order order : query.sort()) {
            Sort.Order sortOrder = new Sort.Order(order.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC,
                    order.field());
            if (order.field().equals("createdAt") || order.field().equals("updatedAt"))
                sortOrder = sortOrder.with(Sort.NullHandling.NULLS_LAST);
            orders.add(sortOrder);
        }
        return PageRequest.of(query.page(), query.size(), Sort.by(orders));
    }

    private Map<Long, List<AdminRoleAssignmentData>> assignmentsByUser(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<Long, List<AdminRoleAssignmentData>> result = new LinkedHashMap<>();
        for (AppUserRole assignment : userRoles.findByUserIdInWithRole(ids)) {
            AppRole role = assignment.getRole();
            result.computeIfAbsent(assignment.getUser().getId(), key -> new ArrayList<>())
                    .add(new AdminRoleAssignmentData(role.getId(), role.getRoleCode(), role.getRoleName(),
                            role.isActive(), assignment.getAssignedBy(), assignment.getAssignedAt()));
        }
        return result;
    }

    private List<AdminRoleAssignmentData> mapAssignments(List<AppUserRole> assignments) {
        return assignments.stream().map(assignment -> {
            AppRole role = assignment.getRole();
            return new AdminRoleAssignmentData(role.getId(), role.getRoleCode(), role.getRoleName(),
                    role.isActive(), assignment.getAssignedBy(), assignment.getAssignedAt());
        }).sorted(Comparator.comparing(AdminRoleAssignmentData::roleId)).toList();
    }

    private AdminAccountData data(AppUser user, List<AdminRoleAssignmentData> assignments) {
        return new AdminAccountData(user.getId(), user.getEmployeeId(), user.getFirstName(), user.getLastName(),
                user.getEmail(), user.getPhoneNumber(), user.getDepartmentId(), user.getAccountStatus(),
                user.getCreatedBy(), user.getCreatedAt(), user.getUpdatedBy(), user.getUpdatedAt(), assignments);
    }

    private RuntimeException translateConstraint(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation) {
                if ("uk_app_users_email".equals(violation.getConstraintName())) return duplicateEmail();
                if ("uk_app_users_employee_id".equals(violation.getConstraintName())) return duplicateEmployee();
                break;
            }
            cause = cause.getCause();
        }
        return exception;
    }

    private AdminAccountManagementFailure userNotFound() {
        return failure(404, "USER_NOT_FOUND", "The requested user does not exist.");
    }

    private AdminAccountManagementFailure selfChange() {
        return failure(409, "SELF_ACCOUNT_CHANGE_NOT_ALLOWED",
                "Changing your own status, roles, or deleting your own account is not allowed.");
    }

    private AdminAccountManagementFailure duplicateEmail() {
        return failure(409, "EMAIL_ALREADY_EXISTS", "The email is already in use.");
    }

    private AdminAccountManagementFailure duplicateEmployee() {
        return failure(409, "EMPLOYEE_ID_ALREADY_EXISTS", "The employee ID is already in use.");
    }

    private AdminAccountManagementFailure failure(int status, String code, String message) {
        return new AdminAccountManagementFailure(status, code, message);
    }
}
