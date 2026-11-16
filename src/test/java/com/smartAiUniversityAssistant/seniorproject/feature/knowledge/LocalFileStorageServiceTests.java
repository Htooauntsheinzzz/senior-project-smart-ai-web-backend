package com.smartAiUniversityAssistant.seniorproject.feature.knowledge;

import com.smartAiUniversityAssistant.seniorproject.config.KnowledgeStorageProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.exception.KnowledgeFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl.LocalFileStorageService;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.smartAiUniversityAssistant.seniorproject.feature.knowledge.KnowledgeTestFiles.*;
import static org.assertj.core.api.Assertions.*;

class LocalFileStorageServiceTests {
    @TempDir Path root;
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T09:00:00Z"), ZoneOffset.UTC);

    private LocalFileStorageService storage(int maxMb) {
        return new LocalFileStorageService(new KnowledgeStorageProperties(true, maxMb,
                new KnowledgeStorageProperties.Storage("local", root.toString(), false)), clock);
    }

    @Test
    void storesUnderAnOpaqueKeyWithChecksumAndPrivatePermissions() throws Exception {
        byte[] content = pdf("checksum");
        var stored = storage(1).store(file("../../handbook.pdf", PDF, content), "pdf");
        assertThat(stored.storageKey()).matches("knowledge/2026/11/[0-9a-f-]{36}\\.pdf").doesNotContain("handbook");
        assertThat(stored.sizeBytes()).isEqualTo(content.length);
        assertThat(stored.sha256Checksum()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)));
        Path path = root.resolve(stored.storageKey());
        assertThat(Files.readAllBytes(path)).isEqualTo(content);
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(path))).isEqualTo("rw-------");
        try (var in = storage(1).open(stored.storageKey())) {
            assertThat(in.readAllBytes()).isEqualTo(content);
        }
        assertThat(Files.list(root.resolve(".incoming")).count()).isZero();
        storage(1).delete(stored.storageKey());
        assertThat(path).doesNotExist();
    }

    @Test
    void enforcesHardByteLimitWhileStreaming() throws Exception {
        assertThatThrownBy(() -> storage(1).store(file("big.txt", TXT, new byte[1024 * 1024 + 1]), "txt"))
                .isInstanceOfSatisfying(KnowledgeFailure.class, f -> assertThat(f.code()).isEqualTo("DOCUMENT_FILE_TOO_LARGE"));
        assertThat(Files.list(root.resolve(".incoming")).count()).isZero();
        try (var files = Files.walk(root.resolve("knowledge"))) {
            assertThat(files.filter(Files::isRegularFile).count()).isZero();
        }
    }

    @Test
    void keysCannotEscapeTheStorageRoot() throws Exception {
        var storage = storage(1);
        for (String key : new String[] {"../outside.pdf", "knowledge/2026/11/../../../x.pdf", "/etc/passwd",
                "knowledge/2026/11/handbook.pdf", "knowledge/2026/11/00000000-0000-0000-0000-000000000000.exe", null})
            assertThatThrownBy(() -> storage.open(key)).isInstanceOf(KnowledgeFailure.class);
        Path outside = Files.createTempFile("outside", ".pdf");
        Path linkDir = root.resolve("knowledge/2026/11");
        Files.createDirectories(linkDir);
        String key = "knowledge/2026/11/00000000-0000-0000-0000-000000000000.pdf";
        Files.createSymbolicLink(root.resolve(key), outside);
        assertThatThrownBy(() -> storage.open(key)).isInstanceOfSatisfying(KnowledgeFailure.class,
                f -> assertThat(f.code()).isEqualTo("DOCUMENT_STORAGE_FAILED"));
        Files.delete(outside);
    }

    @Test
    void missingFilesAndUnwritableStorageAreReportedSafely() throws Exception {
        assertThatThrownBy(() -> storage(1).open("knowledge/2026/11/00000000-0000-0000-0000-000000000000.pdf"))
                .isInstanceOfSatisfying(KnowledgeFailure.class, f -> assertThat(f.code()).isEqualTo("DOCUMENT_FILE_NOT_FOUND"));
        Files.createDirectories(root.resolve("knowledge"));
        Files.setPosixFilePermissions(root.resolve("knowledge"), PosixFilePermissions.fromString("r-x------"));
        try {
            assertThatThrownBy(() -> storage(1).store(file("a.pdf", PDF, pdf("x")), "pdf"))
                    .isInstanceOfSatisfying(KnowledgeFailure.class, f -> assertThat(f.code()).isEqualTo("DOCUMENT_STORAGE_FAILED"));
        } finally {
            Files.setPosixFilePermissions(root.resolve("knowledge"), PosixFilePermissions.fromString("rwx------"));
        }
    }
}
