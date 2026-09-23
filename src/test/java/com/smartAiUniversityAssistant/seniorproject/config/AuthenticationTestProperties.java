package com.smartAiUniversityAssistant.seniorproject.config;
import java.time.Duration;
import java.util.List;
public final class AuthenticationTestProperties {
    private AuthenticationTestProperties() {}
    public static AuthenticationProperties valid() {
        return new AuthenticationProperties(
                new AuthenticationProperties.Jwt("test-issuer","test-audience","test-key",Duration.ofMinutes(15),Duration.ofSeconds(30)),
                new AuthenticationProperties.Session("susa:test:auth",Duration.ofDays(7),Duration.ofMinutes(10),2000),
                new AuthenticationProperties.Password(4,15,72),new AuthenticationProperties.Lockout(5,Duration.ofMinutes(15)),
                new AuthenticationProperties.Cors(List.of("http://localhost:3000")),new AuthenticationProperties.Throttle(60,10,120));
    }
}
