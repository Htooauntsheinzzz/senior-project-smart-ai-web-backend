package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.DocumentUnitType;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.*;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.stereotype.Component;

/**
 * DOCX with Apache POI (spec file 47, section 9), opened read-only. Body elements in document order: headings become
 * "# " marker lines (one # per level, at most six), paragraphs are followed by a blank line, list items start with
 * "- ", table rows are cell texts joined with " | ". A new SECTION unit starts at every level-1 heading, labelled with
 * the heading. Headers, footers, comments, text boxes, embedded objects and tracked deletions are not extracted;
 * tracked insertions are.
 */
@Component
public class DocxTextExtractor implements DocumentTextExtractor {
    private static final int MAX_STYLE_DEPTH = 10;

    @Override public boolean supports(String fileExtension) { return "docx".equals(fileExtension); }

    @Override
    public List<ExtractedUnit> extract(Path file, ExtractionLimits limits) throws IOException {
        try (OPCPackage pkg = OPCPackage.open(file.toFile(), PackageAccess.READ); var document = new XWPFDocument(pkg)) {
            var walker = new Walker(document, limits);
            walker.walk();
            return walker.units.finish();
        } catch (InvalidFormatException e) {
            throw new IOException("invalid package", e);
        }
    }

    private static final class Walker {
        final XWPFDocument document;
        final XWPFStyles styles;
        final ExtractedUnitCollector units;
        boolean blankPending;
        boolean anyLine;
        boolean inList;

        Walker(XWPFDocument document, ExtractionLimits limits) {
            this.document = document;
            this.styles = document.getStyles();
            this.units = new ExtractedUnitCollector(limits);
            units.begin(DocumentUnitType.SECTION, null);
        }

        void walk() {
            for (IBodyElement element : document.getBodyElements()) {
                ExtractedUnitCollector.checkInterrupted();
                if (element instanceof XWPFParagraph paragraph) paragraph(paragraph);
                else if (element instanceof XWPFTable table) table(table);
                else if (element instanceof XWPFSDT sdt) text(sdt.getContent().getText());
            }
        }

        void paragraph(XWPFParagraph paragraph) {
            String text = paragraphText(paragraph);
            if (text.isBlank()) return;
            int level = headingLevel(paragraph);
            if (level > 0) {
                String heading = text.strip().replace('\n', ' ');
                if (level == 1) {
                    units.begin(DocumentUnitType.SECTION, heading.length() > 255 ? heading.substring(0, 255) : heading);
                    anyLine = false;
                    blankPending = false;
                }
                inList = false;
                blank();
                emit("#".repeat(Math.min(level, 6)) + " " + heading);
                blankPending = true;
                return;
            }
            if (paragraph.getNumID() != null) {
                if (!inList) blank();
                inList = true;
                lines("- " + text);
                return;
            }
            if (inList) blankPending = true;
            inList = false;
            text(text);
        }

        void text(String text) {
            if (text == null || text.isBlank()) return;
            blank();
            lines(text);
            blankPending = true;
        }

        void table(XWPFTable table) {
            inList = false;
            var rows = new ArrayList<String>();
            for (XWPFTableRow row : table.getRows()) {
                var cells = new ArrayList<String>();
                for (XWPFTableCell cell : row.getTableCells()) cells.add(cellText(cell));
                if (cells.stream().anyMatch(c -> !c.isBlank())) rows.add(String.join(" | ", cells));
            }
            if (rows.isEmpty()) return;
            blankPending = anyLine;
            blank();
            rows.forEach(this::emit);
            blankPending = true;
        }

        String cellText(XWPFTableCell cell) {
            var parts = new ArrayList<String>();
            for (IBodyElement element : cell.getBodyElements()) {
                if (element instanceof XWPFParagraph p) {
                    String t = paragraphText(p).replace('\n', ' ').strip();
                    if (!t.isEmpty()) parts.add(t);
                } else if (element instanceof XWPFTable nested) {
                    for (XWPFTableRow row : nested.getRows())
                        for (XWPFTableCell inner : row.getTableCells()) {
                            String t = cellText(inner);
                            if (!t.isEmpty()) parts.add(t);
                        }
                }
            }
            return String.join(" ", parts);
        }

        void blank() {
            if (blankPending && anyLine) emit("");
            blankPending = false;
        }

        void lines(String text) {
            for (String line : text.split("\n", -1)) emit(line);
        }

        void emit(String line) {
            units.line(line);
            anyLine = true;
        }

        /** Run text in order. Tracked insertions are runs and are kept; runs inside a tracked deletion or move-away are skipped. */
        static String paragraphText(XWPFParagraph paragraph) {
            var text = new StringBuilder();
            for (IRunElement run : paragraph.getIRuns()) {
                if (run instanceof XWPFRun r) {
                    if (!deleted(r)) text.append(r.text());
                }
                else if (run instanceof XWPFSDT sdt) text.append(sdt.getContent().getText());
            }
            return text.toString().replace('\t', ' ');
        }

        static boolean deleted(XWPFRun run) {
            org.w3c.dom.Node parent = run.getCTR().getDomNode().getParentNode();
            if (parent == null) return false;
            String name = parent.getLocalName();
            return "del".equals(name) || "moveFrom".equals(name);
        }

        /** 0 for body text, else the heading level (Title = 1, heading n = n, outline level n = n + 1). */
        int headingLevel(XWPFParagraph paragraph) {
            Integer outline = outlineLevel(paragraph.getCTP().getPPr());
            if (outline != null) return outline + 1;
            String styleId = paragraph.getStyleID();
            for (int depth = 0; styleId != null && styles != null && depth < MAX_STYLE_DEPTH; depth++) {
                XWPFStyle style = styles.getStyle(styleId);
                if (style == null) break;
                String name = style.getName() == null ? "" : style.getName().strip().toLowerCase(Locale.ROOT);
                if (name.equals("title")) return 1;
                if (name.matches("heading [1-9]")) return name.charAt(8) - '0';
                CTStyle ct = style.getCTStyle();
                if (ct.getPPr() != null && ct.getPPr().getOutlineLvl() != null) {
                    int level = ct.getPPr().getOutlineLvl().getVal().intValue();
                    if (level >= 0 && level <= 8) return level + 1;
                }
                styleId = style.getBasisStyleID();
            }
            return 0;
        }

        static Integer outlineLevel(CTPPr properties) {
            if (properties == null || properties.getOutlineLvl() == null) return null;
            int level = properties.getOutlineLvl().getVal().intValue();
            return level >= 0 && level <= 8 ? level : null;
        }
    }
}
