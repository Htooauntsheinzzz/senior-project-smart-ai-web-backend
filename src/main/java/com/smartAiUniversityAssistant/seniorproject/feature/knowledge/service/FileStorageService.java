package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/** Protected storage for knowledge document bytes, addressed only by backend-generated opaque keys. */
public interface FileStorageService {
    StoredFile store(MultipartFile file, String extension);
    InputStream open(String storageKey);
    void delete(String storageKey);
    /** Stored keys whose files were last modified before the cutoff (for orphan reconciliation). */
    List<String> keysOlderThan(Instant cutoff);
    /** Removes abandoned partial writes older than the cutoff; returns how many were removed. */
    int purgeIncoming(Instant cutoff);

    record StoredFile(String storageKey, long sizeBytes, String sha256Checksum) {}
}
