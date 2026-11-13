package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl;

import com.smartAiUniversityAssistant.seniorproject.config.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.entity.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.IngestionStageType;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.exception.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.repository.AcademicDocumentPageRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.*;
import jakarta.annotation.PreDestroy;
import jakarta.persistence.EntityManager;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The EXTRACT stage (spec file 47). Copies the stored bytes of the current revision into a private temporary file while
 * checking size and SHA-256, parses it on a dedicated thread under a hard timeout while the worker thread keeps the
 * heartbeat, sanitizes the text (section 12) and stores one academic_document_pages row per unit in batches. No
 * transaction is open while a file is copied or parsed. Nothing from the document reaches logs or error messages.
 */
@Component
public class ExtractTextStage implements IngestionStage {
    private static final Logger log = LoggerFactory.getLogger(ExtractTextStage.class);
    private static final Pattern TEMP_NAME = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(pdf|docx|xlsx|txt)$");
    static final int DELETE_BATCH = 500;
    static final int INSERT_UNITS = 50;
    static final int INSERT_CHARS = 500_000;
    private static final long CHECKPOINT_MILLIS = 10_000;

    private final FileStorageService storage;
    private final KnowledgeExtractionProperties properties;
    private final List<DocumentTextExtractor> extractors;
    private final AcademicDocumentPageRepository pages;
    private final EntityManager entityManager;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final Path tempDirectory;
    private ExecutorService executor;

    @Autowired
    public ExtractTextStage(FileStorageService storage, KnowledgeExtractionProperties properties,
            KnowledgeIngestionProperties ingestion, List<DocumentTextExtractor> extractors, AcademicDocumentPageRepository pages,
            EntityManager entityManager, PlatformTransactionManager transactions, Clock clock) {
        if (properties.timeout().compareTo(ingestion.stageTimeout()) >= 0)
            throw new IllegalStateException("app.knowledge.extraction.timeout must be shorter than app.knowledge.ingestion.stage-timeout");
        for (String extension : List.of("pdf", "docx", "xlsx", "txt"))
            if (extractors.stream().filter(e -> e.supports(extension)).count() != 1)
                throw new IllegalStateException("Exactly one text extractor must support " + extension);
        this.storage = storage;
        this.properties = properties;
        this.extractors = List.copyOf(extractors);
        this.pages = pages;
        this.entityManager = entityManager;
        this.transaction = new TransactionTemplate(transactions);
        this.transaction.setTimeout(15);
        this.clock = clock;
        this.tempDirectory = tempDirectory(properties.tempDir());
        this.executor = newExecutor();
        // POI archive limits are JVM-wide: the same bounds as the upload validator (100:1 ratio, 256 MB per entry).
        ZipSecureFile.setMinInflateRatio(0.01);
        ZipSecureFile.setMaxEntrySize(256L * 1024 * 1024);
        // PDFBox writes its font cache here instead of the home directory of the container user.
        if (System.getProperty("pdfbox.fontcache") == null) System.setProperty("pdfbox.fontcache", tempDirectory.toString());
        purgeLeftovers();
    }

    @Override public IngestionStageType type() { return IngestionStageType.EXTRACT; }

    @PreDestroy
    void shutdown() { executor.shutdownNow(); }

    @Override
    public void execute(IngestionJob job, IngestionJobControl control) {
        long started = System.nanoTime();
        control.checkpoint();
        while (tx(() -> pages.deleteBatch(job.fileId(), DELETE_BATCH)) > 0) control.checkpoint();
        Path copy = null;
        Future<List<ExtractedUnit>> task = null;
        CountDownLatch finished = new CountDownLatch(1);
        try {
            copy = copy(job);
            control.checkpoint();
            DocumentTextExtractor extractor = extractors.stream().filter(e -> e.supports(job.fileExtension())).findFirst()
                    .orElseThrow(() -> failure("DOCUMENT_TEXT_EXTRACTION_FAILED"));
            var limits = new ExtractionLimits(properties.maxPages(), properties.maxChars(), properties.unitMaxChars());
            Path file = copy;
            task = executor.submit(() -> {
                try {
                    return extractor.extract(file, limits);
                } finally {
                    finished.countDown();
                }
            });
            List<ExtractedUnit> units = await(job, task, finished, control);
            List<ExtractedUnit> sanitized = units.stream()
                    .map(u -> new ExtractedUnit(u.unitType(), u.label(), sanitize(u.text()))).toList();
            if (sanitized.stream().noneMatch(u -> hasLetterOrDigit(u.text()))) throw failure("DOCUMENT_TEXT_EMPTY");
            store(job, sanitized, control);
            log.info("KNOWLEDGE_EXTRACT documentId={} fileId={} type={} units={} emptyUnits={} chars={} durationMs={}",
                    job.documentId(), job.fileId(), job.fileExtension(), sanitized.size(),
                    sanitized.stream().filter(u -> u.text().isEmpty()).count(),
                    sanitized.stream().mapToLong(u -> u.text().length()).sum(), (System.nanoTime() - started) / 1_000_000);
        } catch (KnowledgeIngestionException e) {
            if (e.stage() == IngestionStageType.EXTRACT)
                log.info("KNOWLEDGE_EXTRACT documentId={} fileId={} type={} failed code={}", job.documentId(), job.fileId(), job.fileExtension(), e.code());
            throw e;
        } finally {
            if (task != null && !task.isDone()) task.cancel(true);
            if (copy != null) deleteQuietly(copy);
        }
    }

    /** Waits for the parser on the worker thread, keeping the heartbeat and enforcing the timeout. */
    private List<ExtractedUnit> await(IngestionJob job, Future<List<ExtractedUnit>> task, CountDownLatch finished,
            IngestionJobControl control) {
        long deadline = System.nanoTime() + properties.timeout().toNanos();
        while (true) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                stop(task, finished);
                throw failure("DOCUMENT_EXTRACTION_TIMEOUT");
            }
            try {
                return task.get(Math.min(TimeUnit.NANOSECONDS.toMillis(remaining) + 1, CHECKPOINT_MILLIS), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                try {
                    control.checkpoint();
                } catch (RuntimeException stopped) {
                    stop(task, finished);
                    throw stopped;
                }
            } catch (InterruptedException e) {
                task.cancel(true);
                Thread.currentThread().interrupt();
                throw KnowledgeIngestionException.interrupted(IngestionStageType.EXTRACT);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof KnowledgeIngestionException k) throw k;
                // Library failures (IOException, RuntimeException, OutOfMemoryError, StackOverflowError): class name only.
                log.info("KNOWLEDGE_EXTRACT documentId={} fileId={} parser error={}", job.documentId(), job.fileId(),
                        cause == null ? "unknown" : cause.getClass().getSimpleName());
                throw failure("DOCUMENT_TEXT_EXTRACTION_FAILED");
            } catch (CancellationException e) {
                throw failure("DOCUMENT_EXTRACTION_TIMEOUT");
            }
        }
    }

    /** Cancels the parser; a thread that ignores the interrupt for 5 seconds is abandoned with a new executor. */
    private synchronized void stop(Future<?> task, CountDownLatch finished) {
        task.cancel(true);
        try {
            if (!finished.await(5, TimeUnit.SECONDS)) {
                log.warn("KNOWLEDGE_EXTRACT parser thread did not stop after cancel; replacing the extraction executor");
                executor.shutdownNow();
                executor = newExecutor();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Copies the stored bytes to a private temporary file and verifies size and SHA-256 against the file row. */
    Path copy(IngestionJob job) {
        Path target;
        try {
            Files.createDirectories(tempDirectory);
            target = tempDirectory.resolve(UUID.randomUUID() + "." + job.fileExtension());
            if (tempDirectory.getFileSystem().supportedFileAttributeViews().contains("posix"))
                Files.createFile(target, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            else Files.createFile(target);
        } catch (IOException e) {
            throw failure("DOCUMENT_STORAGE_UNAVAILABLE");
        }
        boolean ok = false;
        try {
            InputStream in;
            try {
                in = storage.open(job.storageKey());
            } catch (KnowledgeFailure e) {
                throw failure("DOCUMENT_FILE_NOT_FOUND".equals(e.code()) ? "DOCUMENT_FILE_MISSING" : "DOCUMENT_STORAGE_UNAVAILABLE");
            }
            var digest = MessageDigest.getInstance("SHA-256");
            long size = 0;
            try (in; OutputStream out = Files.newOutputStream(target, StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[64 * 1024];
                for (int read; (read = in.read(buffer)) != -1; ) {
                    size += read;
                    if (size > job.sizeBytes()) throw failure("DOCUMENT_CHECKSUM_MISMATCH");
                    digest.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                }
            } catch (IOException e) {
                throw failure("DOCUMENT_STORAGE_UNAVAILABLE");
            }
            if (size != job.sizeBytes() || !HexFormat.of().formatHex(digest.digest()).equals(job.sha256Checksum()))
                throw failure("DOCUMENT_CHECKSUM_MISMATCH");
            ok = true;
            return target;
        } catch (NoSuchAlgorithmException e) {
            throw failure("DOCUMENT_STORAGE_UNAVAILABLE");
        } finally {
            if (!ok) deleteQuietly(target);
        }
    }

    private void store(IngestionJob job, List<ExtractedUnit> units, IngestionJobControl control) {
        var batch = new ArrayList<AcademicDocumentPage>();
        int chars = 0;
        int number = 0;
        for (ExtractedUnit unit : units) {
            var page = new AcademicDocumentPage();
            page.setPageNumber(++number);
            page.setUnitType(unit.unitType());
            page.setUnitLabel(unit.label() == null ? null : sanitize(unit.label()));
            page.setRawText(unit.text());
            page.setRawCharCount(unit.text().length());
            batch.add(page);
            chars += unit.text().length();
            if (batch.size() >= INSERT_UNITS || chars >= INSERT_CHARS) {
                insert(job, batch);
                batch.clear();
                chars = 0;
                control.checkpoint();
            }
        }
        if (!batch.isEmpty()) insert(job, batch);
    }

    private void insert(IngestionJob job, List<AcademicDocumentPage> batch) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        transaction.executeWithoutResult(status -> {
            AcademicDocumentFile file = entityManager.getReference(AcademicDocumentFile.class, job.fileId());
            for (AcademicDocumentPage page : batch) {
                page.setFile(file);
                page.setCreatedAt(now);
            }
            pages.saveAll(batch);
            pages.flush();
            entityManager.clear();
        });
    }

    /**
     * Section 12: the only changes made to stored raw text. U+0000 removed, unpaired surrogates replaced with U+FFFD,
     * \r\n and \r converted to \n, form feeds removed. Nothing else (no trimming, no normalization).
     */
    public static String sanitize(String text) {
        var out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u0000' || c == '\f') continue;
            if (c == '\r') {
                out.append('\n');
                if (i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
            } else if (Character.isHighSurrogate(c)) {
                if (i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
                    out.append(c).append(text.charAt(++i));
                } else out.append('�');
            } else if (Character.isLowSurrogate(c)) out.append('�');
            else out.append(c);
        }
        return out.toString();
    }

    static boolean hasLetterOrDigit(String text) {
        return text.codePoints().anyMatch(Character::isLetterOrDigit);
    }

    static KnowledgeIngestionException failure(String code) {
        String message = switch (code) {
            case "DOCUMENT_FILE_MISSING" -> "The stored file could not be found.";
            case "DOCUMENT_STORAGE_UNAVAILABLE" -> "Document storage is temporarily unavailable.";
            case "DOCUMENT_CHECKSUM_MISMATCH" -> "The stored file does not match the uploaded file.";
            case "DOCUMENT_ENCRYPTED" -> "The document is password-protected or does not allow text extraction.";
            case "DOCUMENT_TEXT_EMPTY" -> "No text could be extracted. Scanned documents are not supported.";
            case "DOCUMENT_TOO_MANY_PAGES" -> "The document has more pages than the configured limit.";
            case "DOCUMENT_TEXT_TOO_LARGE" -> "The document contains more text than the configured limit.";
            case "DOCUMENT_EXTRACTION_TIMEOUT" -> "Text extraction took too long.";
            default -> "The document could not be read. It may be damaged.";
        };
        return new KnowledgeIngestionException(IngestionStageType.EXTRACT, code, "DOCUMENT_STORAGE_UNAVAILABLE".equals(code), message);
    }

    Path tempDirectory() { return tempDirectory; }

    private static Path tempDirectory(String configured) {
        Path directory = configured == null || configured.isBlank()
                ? Path.of(System.getProperty("java.io.tmpdir"), "knowledge-extraction") : Path.of(configured);
        directory = directory.toAbsolutePath().normalize();
        try {
            if (!Files.isDirectory(directory)) {
                if (directory.getFileSystem().supportedFileAttributeViews().contains("posix"))
                    Files.createDirectories(directory, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
                else Files.createDirectories(directory);
            }
            if (!Files.isWritable(directory)) throw new IllegalStateException("app.knowledge.extraction.temp-dir is not writable");
        } catch (IOException e) {
            throw new IllegalStateException("app.knowledge.extraction.temp-dir cannot be created", e);
        }
        return directory;
    }

    /** A crash can leave a working copy behind; only files with the generated name pattern are removed. */
    private void purgeLeftovers() {
        try (var files = Files.list(tempDirectory)) {
            files.filter(p -> TEMP_NAME.matcher(p.getFileName().toString()).matches()).forEach(ExtractTextStage::deleteQuietly);
        } catch (IOException e) {
            log.warn("KNOWLEDGE_EXTRACT could not clean the temporary directory: {}", e.getClass().getSimpleName());
        }
    }

    private static ExecutorService newExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "knowledge-extract");
            thread.setDaemon(true);
            return thread;
        });
    }

    private <T> T tx(java.util.function.Supplier<T> work) { return transaction.execute(status -> work.get()); }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("KNOWLEDGE_EXTRACT could not delete a temporary file: {}", e.getClass().getSimpleName());
        }
    }
}
