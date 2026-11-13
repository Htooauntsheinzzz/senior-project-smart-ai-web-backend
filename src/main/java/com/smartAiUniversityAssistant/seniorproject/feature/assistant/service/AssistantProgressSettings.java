package com.smartAiUniversityAssistant.seniorproject.feature.assistant.service;

import com.smartAiUniversityAssistant.seniorproject.config.AiAssistantProgressProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Step 16A: whether the assistant may read the Student's academic progress totals (AI_ACADEMIC_PROGRESS_ENABLED). Step
 * 16B: whether a question about the Student's own standing gets the hybrid flow (also AI_HYBRID_ANSWERS_ENABLED). The
 * only class that evaluates these conditions; independent of the knowledge base.
 */
@Component
public class AssistantProgressSettings {
    private final boolean active;
    private final boolean hybridEnabled;

    @Autowired
    public AssistantProgressSettings(AiAssistantProgressProperties properties) {
        this(properties.enabled(), properties.hybridEnabled());
    }

    private AssistantProgressSettings(boolean active, boolean hybridEnabled) {
        this.active = active;
        this.hybridEnabled = active && hybridEnabled;
    }

    public boolean active() { return active; }

    /** Step 16B: active() and AI_HYBRID_ANSWERS_ENABLED. */
    public boolean hybridEnabled() { return hybridEnabled; }

    public static AssistantProgressSettings inactive() { return new AssistantProgressSettings(false, false); }

    /** For classes built by hand in tests, and the @Primary test bean that switches the tool on. Hybrid answers off. */
    public static AssistantProgressSettings of(boolean active) { return new AssistantProgressSettings(active, false); }

    /** Step 16B tests: the tool and the hybrid flow. */
    public static AssistantProgressSettings of(boolean active, boolean hybridEnabled) {
        return new AssistantProgressSettings(active, hybridEnabled);
    }
}
