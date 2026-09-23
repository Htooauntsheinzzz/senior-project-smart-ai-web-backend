package com.smartAiUniversityAssistant.seniorproject.config;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import java.util.Locale;

@Configuration(proxyBeanMethods=false) @Profile("prod")
public class ProductionAuthenticationConfig {
    @Bean public SmartInitializingSingleton productionAuthenticationValidation(Environment env,AuthenticationProperties properties) {
        return () -> {
            for(String key: new String[]{"spring.datasource.password","spring.data.redis.password"}) {
                String value=env.getProperty(key,"");
                String lower=value.toLowerCase(Locale.ROOT);
                if(value.length()<16 || lower.contains("replace") || lower.contains("changeme") || lower.contains("local-password")
                        || lower.contains("test-only") || lower.contains("smartrsu"))
                    throw new IllegalStateException("Production authentication requires deployment-specific secrets.");
            }
            if(properties.session().keyPrefix().matches("susa:(dev|test):auth")
                    || properties.cors().allowedOrigins().stream().anyMatch(o -> !o.startsWith("https://"))
                    || !env.getProperty("spring.data.redis.ssl.enabled",Boolean.class,false))
                throw new IllegalStateException("Production authentication requires an isolated namespace, HTTPS origins, and Redis TLS.");
        };
    }
}
