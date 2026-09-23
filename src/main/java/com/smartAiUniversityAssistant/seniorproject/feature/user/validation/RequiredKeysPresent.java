package com.smartAiUniversityAssistant.seniorproject.feature.user.validation;

import jakarta.validation.*;
import java.lang.annotation.*;

@Target(ElementType.TYPE) @Retention(RetentionPolicy.RUNTIME) @Documented
@Constraint(validatedBy = RequiredKeysPresentValidator.class)
public @interface RequiredKeysPresent {
    String message() default "All editable fields must be provided.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
