package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl;

import com.smartAiUniversityAssistant.seniorproject.config.KnowledgeStorageProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.exception.KnowledgeFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.FileStorageService;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stores files under the configured root as knowledge/yyyy/MM/<uuid>.<ext>. Bytes are streamed to a private partial
 * file with a hard size limit and SHA-256, moved atomically into place, then re-read to verify the checksum. Every
 * path is resolved from a key that must match the generated pattern and must stay inside the real storage root.
 */
@Service
public class LocalFileStorageService implements FileStorageService {
    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageService.class);
    private static final Pattern KEY = Pattern.compile(
            "^knowledge/\\d{4}/\\d{2}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(pdf|docx|txt|xlsx)$");
    private static final Set<String> EXTENSIONS = Set.of("pdf", "docx", "txt", "xlsx");
    private static final String INCOMING = ".incoming";
    private final Path configuredRoot;
    private final long maxBytes;
    private final Clock clock;

    public LocalFileStorageService(KnowledgeStorageProperties properties, Clock clock) {
        this.configuredRoot = Path.of(properties.storage().root()).toAbsolutePath().normalize();
        this.maxBytes = properties.maxFileSizeBytes();
        this.clock = clock;
    }

    @Override
    public StoredFile store(MultipartFile file, String extension) {
        if (!EXTENSIONS.contains(extension)) throw KnowledgeFailure.fileTypeUnsupported();
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        String key = "knowledge/%04d/%02d/%s.%s".formatted(today.getYear(), today.getMonthValue(), UUID.randomUUID(), extension);
        Path part = null;
        try {
            Path root = root();
            Path target = resolve(root, key);
            directory(root, target.getParent());
            part = directory(root, root.resolve(INCOMING)).resolve(UUID.randomUUID() + ".part");
            createPrivateFile(part);
            var digest = MessageDigest.getInstance("SHA-256");
            long size = 0;
            try (InputStream in = file.getInputStream(); OutputStream out = Files.newOutputStream(part, StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[64 * 1024];
                for (int read; (read = in.read(buffer)) != -1; ) {
                    size += read;
                    if (size > maxBytes) throw KnowledgeFailure.fileTooLarge();
                    digest.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                }
            }
            if (size == 0) throw KnowledgeFailure.fileEmpty();
            Files.move(part, target, StandardCopyOption.ATOMIC_MOVE);
            String checksum = HexFormat.of().formatHex(digest.digest());
            if (!checksum.equals(sha256(target))) {
                Files.deleteIfExists(target);
                log.error("KNOWLEDGE_STORAGE checksum mismatch after write key={}", key);
                throw KnowledgeFailure.storageFailed();
            }
            return new StoredFile(key, size, checksum);
        } catch (IOException | NoSuchAlgorithmException e) {
            log.error("KNOWLEDGE_STORAGE write failed: {}", e.getClass().getSimpleName());
            throw KnowledgeFailure.storageFailed();
        } finally {
            if (part != null) deleteQuietly(part);
        }
    }

    @Override
    public InputStream open(String storageKey) {
        try {
            Path root = configuredRoot.toRealPath();
            Path path = resolve(root, storageKey);
            if (Files.isSymbolicLink(path) || !path.getParent().toRealPath().startsWith(root)) throw KnowledgeFailure.storageFailed();
            return Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException e) {
            throw KnowledgeFailure.fileNotFound();
        } catch (IOException e) {
            log.error("KNOWLEDGE_STORAGE read failed: {}", e.getClass().getSimpleName());
            throw KnowledgeFailure.storageFailed();
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Path root = configuredRoot.toRealPath();
            Path path = resolve(root, storageKey);
            if (!Files.isSymbolicLink(path) && !path.getParent().toRealPath().startsWith(root)) throw KnowledgeFailure.storageFailed();
            Files.deleteIfExists(path);
        } catch (NoSuchFileException e) {
            // Already gone (or the root was never created).
        } catch (IOException e) {
            log.error("KNOWLEDGE_STORAGE delete failed: {}", e.getClass().getSimpleName());
            throw KnowledgeFailure.storageFailed();
        }
    }

    @Override
    public List<String> keysOlderThan(Instant cutoff) {
        Path base = configuredRoot.resolve("knowledge");
        if (!Files.isDirectory(base, LinkOption.NOFOLLOW_LINKS)) return List.of();
        var keys = new ArrayList<String>();
        try (Stream<Path> paths = Files.walk(base)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) continue;
                String key = configuredRoot.relativize(path).toString().replace(File.separatorChar, '/');
                if (KEY.matcher(key).matches() && Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(cutoff))
                    keys.add(key);
            }
        } catch (IOException e) {
            log.error("KNOWLEDGE_STORAGE scan failed: {}", e.getClass().getSimpleName());
            throw KnowledgeFailure.storageFailed();
        }
        return keys;
    }

    @Override
    public int purgeIncoming(Instant cutoff) {
        Path incoming = configuredRoot.resolve(INCOMING);
        if (!Files.isDirectory(incoming, LinkOption.NOFOLLOW_LINKS)) return 0;
        int removed = 0;
        try (Stream<Path> parts = Files.list(incoming)) {
            for (Path part : (Iterable<Path>) parts::iterator) {
                if (Files.isRegularFile(part, LinkOption.NOFOLLOW_LINKS) && part.getFileName().toString().endsWith(".part")
                        && Files.getLastModifiedTime(part, LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(cutoff)
                        && Files.deleteIfExists(part)) removed++;
            }
        } catch (IOException e) {
            log.error("KNOWLEDGE_STORAGE incoming cleanup failed: {}", e.getClass().getSimpleName());
        }
        return removed;
    }

    /** Resolves a generated key; anything else (including traversal attempts) is refused. */
    static Path resolve(Path root, String storageKey) {
        if (storageKey == null || !KEY.matcher(storageKey).matches()) throw KnowledgeFailure.storageFailed();
        Path path = root.resolve(storageKey).normalize();
        if (!path.startsWith(root)) throw KnowledgeFailure.storageFailed();
        return path;
    }

    private Path root() throws IOException {
        createPrivateDirectories(configuredRoot);
        return configuredRoot.toRealPath();
    }

    /** Creates a directory under the root and verifies that, after following any links, it is still inside it. */
    private Path directory(Path root, Path directory) throws IOException {
        createPrivateDirectories(directory);
        Path real = directory.toRealPath();
        if (!real.startsWith(root)) throw KnowledgeFailure.storageFailed();
        return real;
    }

    private static void createPrivateDirectories(Path directory) throws IOException {
        if (Files.isDirectory(directory)) return;
        if (posix(directory)) Files.createDirectories(directory, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        else Files.createDirectories(directory);
    }

    private static void createPrivateFile(Path file) throws IOException {
        if (posix(file.getParent())) Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        else Files.createFile(file);
    }

    private static boolean posix(Path path) {
        return path.getFileSystem().supportedFileAttributeViews().contains("posix");
    }

    private static String sha256(Path path) throws IOException, NoSuchAlgorithmException {
        var digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[64 * 1024];
            for (int read; (read = in.read(buffer)) != -1; ) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("KNOWLEDGE_STORAGE could not remove partial file: {}", e.getClass().getSimpleName());
        }
    }
}
