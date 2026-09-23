package com.smartAiUniversityAssistant.seniorproject.config;
import jakarta.validation.Validation;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;
class AuthenticationPropertiesTests {
    @Test void validatesBoundsAndExplicitOrigins() {
        var p=AuthenticationTestProperties.valid();
        try(var f=Validation.buildDefaultValidatorFactory()) {
            var v=f.getValidator();assertThat(v.validate(p)).isEmpty();
            assertThat(v.validate(new AuthenticationProperties.Jwt("","aud","kid",Duration.ofMinutes(16),Duration.ofSeconds(31)))).isNotEmpty();
            assertThat(v.validate(new AuthenticationProperties.Session("unscoped",Duration.ZERO,Duration.ofHours(1),2001))).isNotEmpty();
            assertThat(v.validate(new AuthenticationProperties.Password(3,14,73))).isNotEmpty();
            assertThat(v.validate(new AuthenticationProperties.Cors(List.of("*")))).isNotEmpty();
            assertThat(v.validate(new AuthenticationProperties.Cors(List.of("http://localhost:3000/path")))).isNotEmpty();
        }
    }
    @Test void productionRejectsDevelopmentSecretsAtStartup() {
        new ApplicationContextRunner().withUserConfiguration(ProductionAuthenticationConfig.class)
                .withBean(AuthenticationProperties.class,AuthenticationTestProperties::valid)
                .withPropertyValues("spring.profiles.active=prod","spring.datasource.password=replace-with-local-password",
                        "spring.data.redis.password=replace-with-local-password")
                .run(context -> assertThat(context).hasFailed());
    }
}
