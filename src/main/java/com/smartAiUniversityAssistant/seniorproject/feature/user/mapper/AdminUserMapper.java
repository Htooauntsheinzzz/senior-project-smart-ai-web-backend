package com.smartAiUniversityAssistant.seniorproject.feature.user.mapper;

import com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.*;
import com.smartAiUniversityAssistant.seniorproject.feature.user.dto.AdminUserResponse;
import com.smartAiUniversityAssistant.seniorproject.feature.user.dto.AdminUserRoleResponse;
import com.smartAiUniversityAssistant.seniorproject.feature.user.dto.response.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AdminUserMapper {
    public AdminUserResponse response(ProvisionedAdminAccount account) {
        return new AdminUserResponse(account.id(), account.employeeId(), account.phoneNumber(),
                account.firstName(), account.lastName(), account.email(), account.departmentId(),
                account.accountStatus(), List.of(new AdminUserRoleResponse(account.roleId(),
                        account.roleCode(), account.roleName())), account.forcePasswordChange(),
                account.createdBy(), account.createdAt());
    }

    public AdminUserPageResponse page(AdminAccountPageData data, AdminAccountListQuery query) {
        List<AdminUserSummaryResponse> content = data.content().stream().map(this::summary).toList();
        List<String> sort = query.sort().stream()
                .map(order -> order.field() + "," + (order.ascending() ? "asc" : "desc")).toList();
        return new AdminUserPageResponse(content, query.page(), query.size(), data.totalElements(),
                data.totalPages(), query.page() == 0, query.page() >= data.totalPages() - 1, sort);
    }

    public AdminUserSummaryResponse summary(AdminAccountData account) {
        return new AdminUserSummaryResponse(account.id(), account.employeeId(), account.firstName(),
                account.lastName(), account.email(), account.phoneNumber(), account.departmentId(),
                account.accountStatus(), roles(account), instant(account.createdAt()), instant(account.updatedAt()));
    }

    public AdminUserDetailResponse detail(AdminAccountData account) {
        return new AdminUserDetailResponse(account.id(), account.employeeId(), account.firstName(),
                account.lastName(), account.email(), account.phoneNumber(), account.departmentId(),
                account.accountStatus(), roles(account), account.createdBy(), instant(account.createdAt()),
                account.updatedBy(), instant(account.updatedAt()));
    }

    private List<com.smartAiUniversityAssistant.seniorproject.feature.user.dto.response.AdminUserRoleResponse> roles(
            AdminAccountData account) {
        return account.roles().stream().map(role ->
                new com.smartAiUniversityAssistant.seniorproject.feature.user.dto.response.AdminUserRoleResponse(
                        role.roleId(), role.roleCode(), role.roleName(), role.active(),
                        role.assignedBy(), instant(role.assignedAt()))).toList();
    }

    private Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
