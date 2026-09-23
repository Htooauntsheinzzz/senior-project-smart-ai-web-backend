package com.smartAiUniversityAssistant.seniorproject.feature.user.validation;

import jakarta.validation.*;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = PasswordsMatchValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PasswordsMatch {
    String message() default "Passwords must match.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
