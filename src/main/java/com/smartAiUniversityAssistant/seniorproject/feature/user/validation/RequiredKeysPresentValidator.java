package com.smartAiUniversityAssistant.seniorproject.feature.user.validation;

import com.smartAiUniversityAssistant.seniorproject.feature.user.dto.request.UpdateAdminUserRequest;
import jakarta.validation.*;
import java.util.Set;

public class RequiredKeysPresentValidator implements ConstraintValidator<RequiredKeysPresent, UpdateAdminUserRequest> {
    private static final Set<String> REQUIRED = Set.of("employeeId", "phoneNumber", "firstName", "lastName",
            "email", "departmentId", "accountStatus", "roleId");

    @Override
    public boolean isValid(UpdateAdminUserRequest value, ConstraintValidatorContext context) {
        return value == null || value.presentKeys().containsAll(REQUIRED);
    }
}
