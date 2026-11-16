package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl;

import com.smartAiUniversityAssistant.seniorproject.config.KnowledgeIngestionProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.DocumentIngestionService;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

/**
 * Background poller of the ingestion worker: one tick every poll-interval on the scheduler thread, the next tick only
 * after the previous one finished. Not created when scheduler-enabled is false (every test drives runOnce() itself).
 */
@Component
@ConditionalOnProperty(name = "app.knowledge.ingestion.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class KnowledgeIngestionScheduler implements SchedulingConfigurer {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionScheduler.class);
    private final DocumentIngestionService ingestion;
    private final KnowledgeIngestionProperties properties;

    public KnowledgeIngestionScheduler(DocumentIngestionService ingestion, KnowledgeIngestionProperties properties) {
        this.ingestion = ingestion;
        this.properties = properties;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(this::tick, properties.pollInterval());
    }

    /** A failed tick (for example the database is down) is logged once and never throws out of the scheduler. */
    void tick() {
        try {
            ingestion.runOnce();
        } catch (Throwable e) {
            log.warn("KNOWLEDGE_INGESTION tick failed error={}", e.getClass().getSimpleName());
        }
    }
}
