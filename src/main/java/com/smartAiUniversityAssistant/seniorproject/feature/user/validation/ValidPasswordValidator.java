package com.smartAiUniversityAssistant.seniorproject.feature.user.validation;

import com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.PasswordPolicy;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidPasswordValidator implements ConstraintValidator<ValidPassword, String> {
    private final PasswordPolicy policy;
    private boolean requireMinimum;

    public ValidPasswordValidator(PasswordPolicy policy) {
        this.policy = policy;
    }

    @Override
    public void initialize(ValidPassword annotation) {
        requireMinimum = annotation.requireMinimum();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || (requireMinimum ? policy.validNewInput(value) : policy.validInput(value));
    }
}
