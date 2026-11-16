package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl;

import com.smartAiUniversityAssistant.seniorproject.config.KnowledgeStorageProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.exception.KnowledgeFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.KnowledgeFileValidator;
import java.io.*;
import java.nio.charset.*;
import java.text.Normalizer;
import java.util.*;
import java.util.zip.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Allows PDF, DOCX, TXT and XLSX only. The extension, the declared Content-Type and the bytes must all agree:
 * PDF needs the %PDF- header and an %%EOF trailer; TXT must be UTF-8 text without NUL bytes or an HTML/script start;
 * DOCX/XLSX must be a bounded ZIP (entries, expanded size, ratio) with the expected main part and no macros,
 * ActiveX or OLE objects. Nothing is executed or parsed beyond these checks.
 */
@Component
public class KnowledgeFileValidatorImpl implements KnowledgeFileValidator {
    static final Map<String, String> MEDIA_TYPES = Map.of(
            "pdf", "application/pdf",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "txt", "text/plain",
            "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final int MAX_ZIP_ENTRIES = 5000;
    private static final long MAX_EXPANDED_BYTES = 256L * 1024 * 1024;
    private static final long MAX_COMPRESSION_RATIO = 100;
    private static final List<String> FORBIDDEN_ZIP_PARTS = List.of("vbaproject", "activex", "oleobject", "vbadata");
    private static final List<String> EXECUTABLE_SUFFIXES = List.of(".exe", ".dll", ".js", ".vbs", ".bat", ".cmd",
            ".ps1", ".jar", ".msi", ".scr", ".com", ".sh", ".hta");
    private final long maxBytes;

    public KnowledgeFileValidatorImpl(KnowledgeStorageProperties properties) {
        this.maxBytes = properties.maxFileSizeBytes();
    }

    @Override
    public ValidatedFile validate(MultipartFile file) {
        if (file == null) throw KnowledgeFailure.fileRequired();
        if (file.isEmpty()) throw KnowledgeFailure.fileEmpty();
        if (file.getSize() > maxBytes) throw KnowledgeFailure.fileTooLarge();
        String filename = sanitize(file.getOriginalFilename());
        String extension = extension(filename);
        String mediaType = MEDIA_TYPES.get(extension);
        if (mediaType == null || !mediaType.equals(baseType(file.getContentType()))) throw KnowledgeFailure.fileTypeUnsupported();
        try {
            switch (extension) {
                case "pdf" -> pdf(file);
                case "txt" -> text(file);
                case "docx" -> officeOpenXml(file, "word/document.xml");
                default -> officeOpenXml(file, "xl/workbook.xml");
            }
        } catch (IOException e) {
            throw KnowledgeFailure.fileContentInvalid();
        }
        return new ValidatedFile(filename, extension, mediaType);
    }

    /** Last path segment only, NFC, without control or reserved characters, at most 255 characters. */
    static String sanitize(String original) {
        if (original == null) return "";
        String name = original.substring(Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\')) + 1);
        name = Normalizer.normalize(name, Normalizer.Form.NFC);
        var clean = new StringBuilder();
        name.codePoints().filter(c -> !Character.isISOControl(c) && "\"<>:*?|".indexOf(c) < 0
                && Character.getType(c) != Character.FORMAT).forEach(clean::appendCodePoint);
        name = clean.toString().replaceAll("\\s+", " ").trim();
        if (name.length() > 255) {
            int dot = name.lastIndexOf('.');
            String suffix = dot > 0 && name.length() - dot <= 12 ? name.substring(dot) : "";
            name = name.substring(0, 255 - suffix.length()) + suffix;
        }
        return name;
    }

    static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot <= 0 || dot == filename.length() - 1 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String baseType(String contentType) {
        if (contentType == null) return null;
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private static void pdf(MultipartFile file) throws IOException {
        try (InputStream in = file.getInputStream()) {
            byte[] header = in.readNBytes(5);
            if (!Arrays.equals(header, "%PDF-".getBytes(StandardCharsets.US_ASCII))) throw KnowledgeFailure.fileContentInvalid();
            long remaining = file.getSize() - 5;
            if (remaining > 2048) in.skipNBytes(remaining - 2048);
            String tail = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
            if (!tail.contains("%%EOF")) throw KnowledgeFailure.fileContentInvalid();
        }
    }

    private static void text(MultipartFile file) throws IOException {
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        try (var reader = new BufferedReader(new InputStreamReader(file.getInputStream(), decoder))) {
            char[] buffer = new char[16 * 1024];
            var start = new StringBuilder();
            for (int read; (read = reader.read(buffer)) != -1; ) {
                for (int i = 0; i < read; i++) if (buffer[i] == 0) throw KnowledgeFailure.fileContentInvalid();
                if (start.length() < 256) start.append(buffer, 0, Math.min(read, 256));
            }
            String opening = start.toString().replace("\uFEFF", "").stripLeading().toLowerCase(Locale.ROOT);
            for (String markup : List.of("<!doctype html", "<html", "<script", "<svg", "<?xml"))
                if (opening.startsWith(markup)) throw KnowledgeFailure.fileContentInvalid();
        } catch (CharacterCodingException e) {
            throw KnowledgeFailure.fileContentInvalid();
        }
    }

    private static void officeOpenXml(MultipartFile file, String mainPart) throws IOException {
        try (InputStream raw = file.getInputStream()) {
            byte[] signature = raw.readNBytes(4);
            if (!Arrays.equals(signature, new byte[] {'P', 'K', 3, 4})) throw KnowledgeFailure.fileContentInvalid();
        }
        boolean contentTypes = false, main = false;
        int entries = 0;
        long expanded = 0;
        try (var zip = new ZipInputStream(file.getInputStream())) {
            byte[] buffer = new byte[64 * 1024];
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                if (++entries > MAX_ZIP_ENTRIES) throw KnowledgeFailure.fileContentInvalid();
                String name = entry.getName();
                String lower = name.toLowerCase(Locale.ROOT);
                if (name.startsWith("/") || name.contains("..") || name.contains("\\")
                        || FORBIDDEN_ZIP_PARTS.stream().anyMatch(lower::contains)
                        || EXECUTABLE_SUFFIXES.stream().anyMatch(lower::endsWith)) throw KnowledgeFailure.fileContentInvalid();
                if (name.equals(mainPart)) main = true;
                var types = name.equals("[Content_Types].xml") ? new ByteArrayOutputStream() : null;
                if (types != null) contentTypes = true;
                for (int read; (read = zip.read(buffer)) != -1; ) {
                    expanded += read;
                    if (expanded > MAX_EXPANDED_BYTES) throw KnowledgeFailure.fileContentInvalid();
                    if (types != null && types.size() < 1024 * 1024) types.write(buffer, 0, read);
                }
                if (types != null) {
                    String declared = types.toString(StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                    if (declared.contains("macroenabled") || declared.contains("vbaproject")) throw KnowledgeFailure.fileContentInvalid();
                }
            }
        } catch (ZipException e) {
            throw KnowledgeFailure.fileContentInvalid();
        }
        if (!contentTypes || !main || expanded > MAX_COMPRESSION_RATIO * file.getSize()) throw KnowledgeFailure.fileContentInvalid();
    }
}
