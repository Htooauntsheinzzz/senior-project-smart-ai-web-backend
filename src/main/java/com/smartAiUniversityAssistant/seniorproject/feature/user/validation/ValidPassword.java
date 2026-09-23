package com.smartAiUniversityAssistant.seniorproject.feature.user.validation;

import jakarta.validation.*;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ValidPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {
    String message() default "Password does not meet the required policy.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    boolean requireMinimum() default false;
}
