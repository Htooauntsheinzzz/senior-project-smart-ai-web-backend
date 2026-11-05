package com.smartAiUniversityAssistant.seniorproject.feature.knowledge;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.springframework.mock.web.MockMultipartFile;

/** Small synthetic test documents; no real university document is ever used. */
public final class KnowledgeTestFiles {
    public static final String PDF = "application/pdf";
    public static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    public static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String TXT = "text/plain";

    private KnowledgeTestFiles() {}

    public static byte[] pdf(String text) {
        return ("%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\n% " + text + "\ntrailer << /Root 1 0 R >>\n%%EOF\n")
                .getBytes(StandardCharsets.ISO_8859_1);
    }
    public static byte[] docx() throws IOException {
        return zip(Map.of("[Content_Types].xml", contentTypes("wordprocessingml.document.main+xml"),
                "word/document.xml", "<w:document><w:body><w:p>Handbook</w:p></w:body></w:document>"));
    }
    public static byte[] xlsx() throws IOException {
        return zip(Map.of("[Content_Types].xml", contentTypes("spreadsheetml.sheet.main+xml"),
                "xl/workbook.xml", "<workbook><sheets><sheet name=\"Calendar\"/></sheets></workbook>"));
    }
    public static String contentTypes(String main) {
        return "<?xml version=\"1.0\"?><Types><Override ContentType=\"application/vnd.openxmlformats-officedocument." + main + "\"/></Types>";
    }
    public static byte[] zip(Map<String, String> entries) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (var entry : new TreeMap<>(entries).entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
    public static MockMultipartFile file(String filename, String contentType, byte[] content) {
        return new MockMultipartFile("file", filename, contentType, content);
    }
}
