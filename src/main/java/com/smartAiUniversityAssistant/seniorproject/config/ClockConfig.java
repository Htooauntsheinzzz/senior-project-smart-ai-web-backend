package com.smartAiUniversityAssistant.seniorproject.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({AuthenticationProperties.class, AiAssistantProperties.class,
        AiAssistantContextProperties.class, KnowledgeStorageProperties.class, StudentAcademicProperties.class, CampusProperties.class,
        KnowledgeIngestionProperties.class, KnowledgeExtractionProperties.class, KnowledgeCleaningProperties.class,
        KnowledgeChunkingProperties.class, KnowledgeEmbeddingProperties.class, KnowledgeSearchProperties.class,
        AiAssistantKnowledgeProperties.class, AiAssistantProgressProperties.class,
        AiAssistantHistoryProperties.class})
public class ClockConfig {
    @Bean public Clock authenticationClock() { return Clock.systemUTC(); }
    @Bean public PasswordEncoder passwordEncoder(AuthenticationProperties properties) {
        return new BCryptPasswordEncoder(properties.password().bcryptStrength());
    }
}
