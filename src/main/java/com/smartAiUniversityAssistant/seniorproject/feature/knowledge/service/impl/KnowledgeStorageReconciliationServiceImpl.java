package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl;

import com.smartAiUniversityAssistant.seniorproject.config.KnowledgeStorageProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.repository.AcademicDocumentFileRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.*;
import java.time.*;
import org.slf4j.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Runs once at startup. Only files older than an hour are considered, so uploads still in flight are never touched;
 * abandoned partial writes are always removed, completed orphans are reported and removed only on request.
 */
@Service
public class KnowledgeStorageReconciliationServiceImpl implements KnowledgeStorageReconciliationService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeStorageReconciliationServiceImpl.class);
    private static final Duration MIN_AGE = Duration.ofHours(1);
    private final FileStorageService storage;
    private final AcademicDocumentFileRepository files;
    private final KnowledgeStorageProperties properties;
    private final Clock clock;

    public KnowledgeStorageReconciliationServiceImpl(FileStorageService storage, AcademicDocumentFileRepository files,
            KnowledgeStorageProperties properties, Clock clock) {
        this.storage = storage;
        this.files = files;
        this.properties = properties;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        try {
            reconcile();
        } catch (RuntimeException e) {
            log.warn("KNOWLEDGE_STORAGE reconciliation skipped: {}", e.getClass().getSimpleName());
        }
    }

    @Override
    public int reconcile() {
        Instant cutoff = clock.instant().minus(MIN_AGE);
        int parts = storage.purgeIncoming(cutoff);
        int orphans = 0;
        for (String key : storage.keysOlderThan(cutoff)) {
            if (files.existsByStorageKey(key)) continue;
            orphans++;
            if (properties.storage().deleteOrphans()) storage.delete(key);
            log.warn("KNOWLEDGE_STORAGE orphan key={} deleted={}", key, properties.storage().deleteOrphans());
        }
        log.info("KNOWLEDGE_STORAGE reconciliation partialFilesRemoved={} orphans={}", parts, orphans);
        return orphans;
    }
}
