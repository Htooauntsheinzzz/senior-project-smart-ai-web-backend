package com.smartAiUniversityAssistant.seniorproject.feature.user.validation;

import com.smartAiUniversityAssistant.seniorproject.feature.user.dto.CreateAdminUserRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Objects;

public class PasswordsMatchValidator implements ConstraintValidator<PasswordsMatch, CreateAdminUserRequest> {
    @Override
    public boolean isValid(CreateAdminUserRequest request, ConstraintValidatorContext context) {
        return request == null || Objects.equals(request.temporaryPassword(), request.confirmPassword());
    }
}
