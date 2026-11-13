package com.smartAiUniversityAssistant.seniorproject.feature.knowledge;

import com.smartAiUniversityAssistant.seniorproject.config.KnowledgeIngestionProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.exception.KnowledgeFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl.DocumentIngestionServiceImpl;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

import static com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.DocumentProcessingStatus.*;
import static org.assertj.core.api.Assertions.*;

/** Spec file 46, section 20.1: no Spring context and no database. */
class DocumentIngestionUnitTests {
    private static final Map<DocumentProcessingStatus, Set<DocumentProcessingStatus>> ALLOWED = Map.of(
            UPLOADED, Set.of(QUEUED),
            QUEUED, Set.of(PROCESSING, FAILED),
            PROCESSING, Set.of(CHUNKING, FAILED),
            CHUNKING, Set.of(EMBEDDING, FAILED),
            EMBEDDING, Set.of(READY, FAILED),
            FAILED, Set.of(QUEUED),
            READY, Set.of(QUEUED));

    @Test
    void everyTransitionPairMatchesTheMatrix() {
        for (DocumentProcessingStatus from : DocumentProcessingStatus.values())
            for (DocumentProcessingStatus to : DocumentProcessingStatus.values()) {
                boolean allowed = ALLOWED.get(from).contains(to);
                assertThat(from.canTransitionTo(to)).as(from + " -> " + to).isEqualTo(allowed);
                if (allowed) assertThatCode(() -> from.requireTransitionTo(to)).doesNotThrowAnyException();
                else assertThatThrownBy(() -> from.requireTransitionTo(to)).isInstanceOf(IllegalStateException.class);
            }
    }

    @Test
    void stageTypesMapToTheirStatus() {
        assertThat(IngestionStageType.EXTRACT.status()).isEqualTo(PROCESSING);
        assertThat(IngestionStageType.CLEAN.status()).isEqualTo(PROCESSING);
        assertThat(IngestionStageType.CHUNK.status()).isEqualTo(CHUNKING);
        assertThat(IngestionStageType.EMBED.status()).isEqualTo(EMBEDDING);
        assertThat(IngestionStageType.INDEX.status()).isEqualTo(EMBEDDING);
        assertThat(IngestionStageType.values()).containsExactly(IngestionStageType.EXTRACT, IngestionStageType.CLEAN,
                IngestionStageType.CHUNK, IngestionStageType.EMBED, IngestionStageType.INDEX);
    }

    @Test
    void retryDelayGrowsAndIsCapped() {
        var properties = properties(true, 4, Duration.ofHours(6));
        assertThat(properties.retryDelay(1)).isEqualTo(Duration.ofMinutes(2));
        assertThat(properties.retryDelay(2)).isEqualTo(Duration.ofMinutes(10));
        assertThat(properties.retryDelay(3)).isEqualTo(Duration.ofMinutes(50));
        assertThat(properties(true, 10, Duration.ofMinutes(30)).retryDelay(3)).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties(true, 10, Duration.ofHours(6)).retryDelay(9)).isEqualTo(Duration.ofHours(6));
    }

    @Test
    void twoStagesOfOneTypeAreRejected() {
        assertThatThrownBy(() -> worker(properties(true, 4, Duration.ofHours(6)),
                List.of(stage(IngestionStageType.EXTRACT), stage(IngestionStageType.EXTRACT))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("EXTRACT");
    }

    @Test
    void withoutStagesReprocessIsNotAvailable() {
        var worker = worker(properties(true, 4, Duration.ofHours(6)), List.of());
        assertThat(worker.ingestionAvailable()).isFalse();
        assertThatThrownBy(() -> worker.requestProcessing(1, 1, ReprocessMode.RESUME))
                .isInstanceOfSatisfying(KnowledgeFailure.class, e -> assertThat(e.code()).isEqualTo("DOCUMENT_PROCESSING_NOT_AVAILABLE"));
    }

    @Test
    void disabledIngestionDoesNothing() {
        var all = Arrays.stream(IngestionStageType.values()).map(DocumentIngestionUnitTests::stage).toList();
        var worker = worker(properties(false, 4, Duration.ofHours(6)), all);
        // The repositories are null: any database access would throw.
        assertThatCode(worker::runOnce).doesNotThrowAnyException();
        assertThat(worker.ingestionAvailable()).isFalse();
        assertThatThrownBy(() -> worker.requestProcessing(1, 1, ReprocessMode.FULL))
                .isInstanceOfSatisfying(KnowledgeFailure.class, e -> {
                    assertThat(e.code()).isEqualTo("DOCUMENT_PROCESSING_NOT_AVAILABLE");
                    assertThat(e.status()).isEqualTo(503);
                });
        assertThat(worker(properties(true, 4, Duration.ofHours(6)), all).ingestionAvailable()).isTrue();
    }

    private static DocumentIngestionServiceImpl worker(KnowledgeIngestionProperties properties, List<IngestionStage> stages) {
        return new DocumentIngestionServiceImpl(null, null, Clock.systemUTC(), properties, null, stages, null, null);
    }

    private static KnowledgeIngestionProperties properties(boolean enabled, int maxAttempts, Duration maxDelay) {
        return new KnowledgeIngestionProperties(enabled, false, true, Duration.ofSeconds(5), maxAttempts, Duration.ofMinutes(2),
                5, maxDelay, Duration.ofMinutes(15), Duration.ofMinutes(30));
    }

    private static IngestionStage stage(IngestionStageType type) {
        return new IngestionStage() {
            public IngestionStageType type() { return type; }
            public void execute(IngestionJob job, IngestionJobControl control) {}
        };
    }
}
