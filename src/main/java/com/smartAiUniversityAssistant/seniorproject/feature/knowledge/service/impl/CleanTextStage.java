package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl;

import com.smartAiUniversityAssistant.seniorproject.config.KnowledgeCleaningProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.entity.AcademicDocumentPage;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.exception.KnowledgeIngestionException;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.repository.AcademicDocumentPageRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.KnowledgeTextCleaner.*;
import java.util.*;
import org.slf4j.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The CLEAN stage (spec file 48). Reads raw_text of every page of the file, 50 at a time, and writes cleaned_text and
 * cleaned_char_count; raw_text is never changed and cleaning always starts from it. Pass 1 (PDF files with at least four
 * pages with text) only collects the few top and bottom lines of each page. No transaction is open while text is cleaned.
 */
@Component
public class CleanTextStage implements IngestionStage {
    private static final Logger log = LoggerFactory.getLogger(CleanTextStage.class);
    static final int BATCH = 50;
    /** More undecodable characters than this share of the non-space characters fails the file (section 13). */
    static final double UNREADABLE_SHARE = 0.2;

    private final AcademicDocumentPageRepository pages;
    private final KnowledgeTextCleaner cleaner;
    private final TransactionTemplate transaction;
    private final TransactionTemplate readOnly;

    public CleanTextStage(AcademicDocumentPageRepository pages, KnowledgeCleaningProperties properties,
            PlatformTransactionManager transactions) {
        this.pages = pages;
        this.cleaner = new KnowledgeTextCleaner(properties.unwrapLines(), properties.removeRepeatedLines(), properties.thaiDigitsToAscii());
        this.transaction = new TransactionTemplate(transactions);
        this.transaction.setTimeout(15);
        this.readOnly = new TransactionTemplate(transactions);
        this.readOnly.setTimeout(15);
        this.readOnly.setReadOnly(true);
    }

    @Override public IngestionStageType type() { return IngestionStageType.CLEAN; }

    @Override
    public void execute(IngestionJob job, IngestionJobControl control) {
        long started = System.nanoTime();
        control.checkpoint();
        RepeatedLines repeated = RepeatedLines.none();
        if (cleaner.removeRepeatedLines()) {
            RepeatedLineDetector detector = cleaner.detector();
            boolean anyPage = false;
            int after = 0;
            for (List<AcademicDocumentPage> batch; !(batch = batch(job.fileId(), after)).isEmpty(); ) {
                for (AcademicDocumentPage page : batch) {
                    if (page.getUnitType() == DocumentUnitType.PAGE) {
                        anyPage = true;
                        guarded(() -> detector.add(page.getPageNumber(), page.getRawText()));
                    }
                    after = page.getPageNumber();
                }
                control.checkpoint();
            }
            if (anyPage) repeated = guarded(detector::result);
        }
        var stats = new Stats();
        boolean anyText = false;
        int count = 0;
        long rawChars = 0, cleanedChars = 0;
        int after = 0;
        for (List<AcademicDocumentPage> batch; !(batch = batch(job.fileId(), after)).isEmpty(); ) {
            var updates = new ArrayList<Object[]>();
            for (AcademicDocumentPage page : batch) {
                RepeatedLines lines = repeated;
                Cleaned cleaned = guarded(() -> cleaner.clean(page.getUnitType(), page.getPageNumber(), page.getRawText(), lines));
                stats.add(cleaned.stats());
                anyText |= KnowledgeTextCleaner.hasLetterOrDigit(cleaned.text());
                rawChars += page.getRawCharCount();
                cleanedChars += cleaned.text().length();
                updates.add(new Object[] {page.getId(), cleaned.text()});
                after = page.getPageNumber();
                count++;
            }
            transaction.executeWithoutResult(status -> {
                for (Object[] update : updates) pages.updateCleaned((Long) update[0], (String) update[1], ((String) update[1]).length());
            });
            control.checkpoint();
        }
        log.info("KNOWLEDGE_CLEAN documentId={} fileId={} version={} pages={} rawChars={} cleanedChars={} pageNumberLines={} "
                        + "repeatedLines={} joinedLines={} hyphenJoins={} undecodable={} durationMs={}",
                job.documentId(), job.fileId(), KnowledgeTextCleaner.VERSION, count, rawChars, cleanedChars, stats.pageNumberLines,
                stats.repeatedLines, stats.joinedLines, stats.hyphenJoins, stats.replacementChars + stats.removedPrivateUse,
                (System.nanoTime() - started) / 1_000_000);
        if (!anyText) throw failure("DOCUMENT_TEXT_EMPTY", "No usable text remained after cleaning.");
        long undecodable = stats.replacementChars + stats.removedPrivateUse;
        long total = stats.nonSpaceChars + stats.removedPrivateUse;
        if (total > 0 && undecodable > UNREADABLE_SHARE * total)
            throw failure("DOCUMENT_TEXT_UNREADABLE", "The document text could not be decoded. Upload a different version of the file.");
    }

    private List<AcademicDocumentPage> batch(long fileId, int after) {
        return readOnly.execute(status -> pages.findByFileIdAndPageNumberGreaterThanOrderByPageNumberAsc(fileId, after, PageRequest.of(0, BATCH)));
    }

    /** Any unexpected error inside the cleaner is DOCUMENT_TEXT_CLEANING_FAILED; only its class name is logged. */
    private <T> T guarded(java.util.function.Supplier<T> work) {
        try {
            return work.get();
        } catch (RuntimeException | StackOverflowError e) {
            log.warn("KNOWLEDGE_CLEAN cleaner error={}", e.getClass().getSimpleName());
            throw failure("DOCUMENT_TEXT_CLEANING_FAILED", "The document text could not be cleaned.");
        }
    }

    private void guarded(Runnable work) {
        guarded(() -> {
            work.run();
            return null;
        });
    }

    private static KnowledgeIngestionException failure(String code, String message) {
        return new KnowledgeIngestionException(IngestionStageType.CLEAN, code, false, message);
    }
}
