package com.smartAiUniversityAssistant.seniorproject.feature.knowledge;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.encryption.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;

/**
 * Generates real, parseable test documents in code (spec file 47, section 20.1). All text is synthetic; no real
 * university document is used. KnowledgeTestFiles keeps the unparseable "damaged" fixtures.
 */
public final class KnowledgeRealFiles {
    private KnowledgeRealFiles() {}

    /** One page per entry; an empty string gives an empty page, null gives a page with only a drawing. */
    public static byte[] pdf(String... pages) throws IOException {
        return pdf(null, pages);
    }

    public static byte[] pdf(StandardProtectionPolicy protection, String... pages) throws IOException {
        try (var document = new PDDocument()) {
            var font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (String text : pages) {
                var page = new PDPage();
                document.addPage(page);
                try (var content = new PDPageContentStream(document, page)) {
                    if (text == null) {
                        content.addRect(100, 100, 200, 200);
                        content.fill();
                        continue;
                    }
                    if (text.isEmpty()) continue;
                    content.beginText();
                    content.setFont(font, 11);
                    content.setLeading(14);
                    content.newLineAtOffset(72, 720);
                    for (String line : text.split("\n", -1)) {
                        content.showText(line);
                        content.newLine();
                    }
                    content.endText();
                }
            }
            if (protection != null) document.protect(protection);
            var out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    /** Pages that contain only a drawing, like a scanned document without a text layer. */
    public static byte[] scannedPdf(int pages) throws IOException {
        return pdf(null, new String[pages]);
    }

    public static byte[] passwordPdf(String text) throws IOException {
        var policy = new StandardProtectionPolicy("owner-secret", "user-secret", new AccessPermission());
        policy.setEncryptionKeyLength(128);
        return pdf(policy, text);
    }

    public static byte[] noExtractionPdf(String text) throws IOException {
        var permissions = new AccessPermission();
        permissions.setCanExtractContent(false);
        var policy = new StandardProtectionPolicy("owner-secret", "", permissions);
        policy.setEncryptionKeyLength(128);
        return pdf(policy, text);
    }

    /** A DOCX builder with real heading styles (style names "heading 1", "heading 2", "Title"). */
    public static final class Docx {
        private final XWPFDocument document = new XWPFDocument();

        public Docx() {
            XWPFStyles styles = document.createStyles();
            style(styles, "Title", "Title", null);
            style(styles, "Heading1", "heading 1", null);
            style(styles, "Heading2", "heading 2", null);
            style(styles, "MyChapter", "My Chapter", "Heading1");
        }

        private static void style(XWPFStyles styles, String id, String name, String basedOn) {
            CTStyle style = CTStyle.Factory.newInstance();
            style.setStyleId(id);
            style.addNewName().setVal(name);
            style.setType(STStyleType.PARAGRAPH);
            if (basedOn != null) style.addNewBasedOn().setVal(basedOn);
            styles.addStyle(new XWPFStyle(style));
        }

        public Docx heading(String styleId, String text) {
            XWPFParagraph p = document.createParagraph();
            p.setStyle(styleId);
            p.createRun().setText(text);
            return this;
        }

        public Docx paragraph(String text) {
            document.createParagraph().createRun().setText(text);
            return this;
        }

        public Docx listItem(String text) {
            XWPFParagraph p = document.createParagraph();
            p.setNumID(BigInteger.ONE);
            p.createRun().setText(text);
            return this;
        }

        public Docx table(String[]... rows) {
            XWPFTable table = document.createTable(rows.length, rows[0].length);
            for (int r = 0; r < rows.length; r++)
                for (int c = 0; c < rows[r].length; c++) table.getRow(r).getCell(c).setText(rows[r][c]);
            return this;
        }

        public Docx headerAndFooter(String header, String footer) {
            document.createHeader(HeaderFooterType.DEFAULT).createParagraph().createRun().setText(header);
            document.createFooter(HeaderFooterType.DEFAULT).createParagraph().createRun().setText(footer);
            return this;
        }

        /** A paragraph with kept text, a tracked insertion and a tracked deletion. */
        public Docx trackedChanges(String kept, String inserted, String deleted) {
            XWPFParagraph p = document.createParagraph();
            p.createRun().setText(kept);
            CTP ctp = p.getCTP();
            CTRunTrackChange ins = ctp.addNewIns();
            ins.setId(BigInteger.ONE);
            ins.setAuthor("tester");
            ins.addNewR().addNewT().setStringValue(inserted);
            CTRunTrackChange del = ctp.addNewDel();
            del.setId(BigInteger.TWO);
            del.setAuthor("tester");
            del.addNewR().addNewDelText().setStringValue(deleted);
            return this;
        }

        public byte[] bytes() throws IOException {
            var out = new ByteArrayOutputStream();
            document.write(out);
            document.close();
            return out.toByteArray();
        }
    }

    /** Two visible sheets (text, numbers, a date, a formula with cached result, a gap) and one hidden sheet. */
    public static byte[] xlsx() throws IOException {
        try (var workbook = new XSSFWorkbook()) {
            Sheet courses = workbook.createSheet("Courses");
            row(courses, 0, "Code", "Title", "Credits");
            row(courses, 1, "CS301", "Algorithms", 3.0);
            Row gap = courses.createRow(2);
            gap.createCell(0).setCellValue("CS302");
            gap.createCell(2).setCellValue(4);
            Row total = courses.createRow(4);
            total.createCell(0).setCellValue("Total");
            total.createCell(2).setCellFormula("C2+C3");
            Sheet calendar = workbook.createSheet("Calendar");
            Row date = calendar.createRow(0);
            date.createCell(0).setCellValue("Semester start");
            Cell cell = date.createCell(1);
            cell.setCellValue(java.time.LocalDate.of(2026, 6, 15));
            CellStyle style = workbook.createCellStyle();
            style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
            cell.setCellStyle(style);
            Row fee = calendar.createRow(1);
            fee.createCell(0).setCellValue("Late fee");
            fee.createCell(1).setCellValue(1500.5);
            Sheet hidden = workbook.createSheet("Secret");
            row(hidden, 0, "HIDDEN SHEET TEXT");
            workbook.setSheetHidden(2, true);
            workbook.createSheet("Empty");
            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            var out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static void row(Sheet sheet, int index, Object... values) {
        Row row = sheet.createRow(index);
        for (int i = 0; i < values.length; i++) {
            if (values[i] instanceof String s) row.createCell(i).setCellValue(s);
            else row.createCell(i).setCellValue(((Number) values[i]).doubleValue());
        }
    }

    public static byte[] utf8(String text) { return text.getBytes(StandardCharsets.UTF_8); }
}
