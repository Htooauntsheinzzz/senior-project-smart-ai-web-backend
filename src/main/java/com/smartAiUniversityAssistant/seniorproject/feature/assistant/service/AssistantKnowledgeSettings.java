package com.smartAiUniversityAssistant.seniorproject.feature.assistant.service;

import com.smartAiUniversityAssistant.seniorproject.config.AiAssistantKnowledgeProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.EmbeddingClient;
import java.util.function.BooleanSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Step 15A: whether the assistant may search the knowledge base, and the limits of that search. The tool is active
 * when AI_KNOWLEDGE_ENABLED is true and an embedding model is configured (spec file 50). This is the only class that
 * evaluates that condition.
 */
@Component
public class AssistantKnowledgeSettings {
    private final BooleanSupplier active;
    private final int topK;
    private final int maxContextChars;
    private final int maxSearchesPerRequest;
    private final int ownProgramQuota;
    private final int maxNeighborPassages;

    @Autowired
    public AssistantKnowledgeSettings(AiAssistantKnowledgeProperties properties, EmbeddingClient embeddingClient) {
        this(() -> properties.enabled() && embeddingClient.configured(), properties.topK(), properties.maxContextChars(),
                properties.maxSearchesPerRequest(), properties.ownProgramQuota(), properties.maxNeighborPassages());
    }

    private AssistantKnowledgeSettings(BooleanSupplier active, int topK, int maxContextChars, int maxSearchesPerRequest,
            int ownProgramQuota, int maxNeighborPassages) {
        this.active = active;
        this.topK = topK;
        this.maxContextChars = maxContextChars;
        this.maxSearchesPerRequest = maxSearchesPerRequest;
        this.ownProgramQuota = ownProgramQuota;
        this.maxNeighborPassages = maxNeighborPassages;
    }

    public boolean active() { return active.getAsBoolean(); }
    public int topK() { return topK; }
    public int maxContextChars() { return maxContextChars; }
    public int maxSearchesPerRequest() { return maxSearchesPerRequest; }
    /** Step 15B: places of one search kept for passages of the Student's own program. */
    public int ownProgramQuota() { return ownProgramQuota; }
    /** Step 15B: neighbouring chunks that one search may add. */
    public int maxNeighborPassages() { return maxNeighborPassages; }

    /** For code and tests that run without the feature. */
    public static AssistantKnowledgeSettings inactive() {
        return new AssistantKnowledgeSettings(() -> false, 4, 8000, 2, 0, 0);
    }

    /** For tests that build the assistant by hand with the feature switched on. */
    public static AssistantKnowledgeSettings active(int topK, int maxContextChars, int maxSearchesPerRequest) {
        return active(topK, maxContextChars, maxSearchesPerRequest, 0, 0);
    }

    public static AssistantKnowledgeSettings active(int topK, int maxContextChars, int maxSearchesPerRequest, int ownProgramQuota,
            int maxNeighborPassages) {
        return new AssistantKnowledgeSettings(() -> true, topK, maxContextChars, maxSearchesPerRequest, ownProgramQuota,
                maxNeighborPassages);
    }
}
