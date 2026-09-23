package com.smartAiUniversityAssistant.seniorproject.feature.authentication;
import com.smartAiUniversityAssistant.seniorproject.config.AuthenticationTestProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.exception.AuthenticationFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.*;
import com.smartAiUniversityAssistant.seniorproject.security.TokenSupport;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class AuthenticationPolicyTests {
    private final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder(4);
    private final PasswordPolicy policy=new PasswordPolicy(AuthenticationTestProperties.valid(),encoder);
    @Test void unicodeBytesCodePointsAndNoNormalization() {
        assertThat(policy.validInput("😀".repeat(18))).isTrue();
        assertThat(policy.validInput("😀".repeat(19))).isFalse();
        assertThat(policy.validInput("x".repeat(72))).isTrue();
        assertThat(policy.validInput("x".repeat(73))).isFalse();
        assertThat(policy.validInput("x\0y")).isFalse();assertThat(policy.validInput("\uD800")).isFalse();
        String hash=encoder.encode(" padded legacy ");
        assertThat(policy.matches(" padded legacy ",hash)).isTrue();assertThat(policy.matches("padded legacy",hash)).isFalse();
        assertThatCode(() -> policy.validateNew("😀".repeat(18),hash)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.validateNew("😀".repeat(14),hash)).isInstanceOf(AuthenticationFailure.class);
        assertThatThrownBy(() -> policy.validateNew(" padded legacy ",hash)).isInstanceOf(AuthenticationFailure.class);
        assertThat(policy.matches("anything","corrupt")).isFalse();
        assertThat(policy.matches(" padded legacy ",hash.replace("$2a$","$2b$"))).isTrue();
        assertThat(policy.matches(" padded legacy ",hash.replace("$2a$","$2y$"))).isTrue();
    }
    @Test void emailTrimsOnlySurroundingsAndDtoErrorsDoNotExposeSecrets() {
        var request=new LoginRequest(" Admin@Example.test "," secret ");
        assertThat(request.email()).isEqualTo("Admin@Example.test");assertThat(request.password()).isEqualTo(" secret ");
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var validator=factory.getValidator();
            assertThat(validator.validate(new LoginRequest("invalid","secret"))).isNotEmpty();
            assertThat(validator.validate(new LoginRequest("a@b.test",""))).isNotEmpty();
            assertThat(validator.validate(new RefreshTokenRequest("a".repeat(513)))).isNotEmpty();
        }
        assertThat(request.toString()).doesNotContain("secret","Admin");
        assertThat(new ChangePasswordRequest("old-secret","new-secret").toString()).doesNotContain("old-secret","new-secret");
    }
    @Test void canonicalRefreshParsingAndDigestNeverStoreRawToken() {
        var token=RefreshToken.generate(Long.MAX_VALUE,TokenSupport.random(16));
        assertThat(RefreshToken.parse(token.value())).isEqualTo(token);
        assertThat(token.digest()).matches("[0-9a-f]{64}");assertThat(token.toString()).doesNotContain(token.value());
        var parts=token.value().split("\\.");
        for(String bad:List.of("",token.value()+"=",token.value()+".extra","rt2.1."+parts[2]+"."+parts[3],
                "rt1.01."+parts[2]+"."+parts[3],"rt1.0."+parts[2]+"."+parts[3],"rt1.9223372036854775808."+parts[2]+"."+parts[3],
                "rt1.1.{injection}."+parts[3],"rt1.1."+parts[2]+".short")) {
            assertThatThrownBy(() -> RefreshToken.parse(bad)).isInstanceOf(AuthenticationFailure.class);
        }
    }
}
