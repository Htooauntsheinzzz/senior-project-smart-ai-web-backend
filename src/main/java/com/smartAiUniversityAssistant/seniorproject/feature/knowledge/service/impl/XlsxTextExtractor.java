package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.DocumentUnitType;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.*;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import javax.xml.stream.*;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.*;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.eventusermodel.*;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.springframework.stereotype.Component;
import org.xml.sax.*;

/**
 * XLSX with the Apache POI streaming reader (spec file 47, section 10); never XSSFWorkbook. One SHEET unit per visible
 * sheet that has a non-empty cell, labelled with the sheet name. Hidden and very hidden sheets are skipped (their state
 * is read from the workbook part with a hardened parser). One line per non-empty row: cell values as displayed
 * (DataFormatter, Locale.US, cached formula results) joined with " | ", gaps kept, trailing empty cells dropped.
 */
@Component
public class XlsxTextExtractor implements DocumentTextExtractor {
    private static final String RELATIONSHIPS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    @Override public boolean supports(String fileExtension) { return "xlsx".equals(fileExtension); }

    record SheetEntry(String name, String state, String relationId) {}

    @Override
    public List<ExtractedUnit> extract(Path file, ExtractionLimits limits) throws IOException {
        try (OPCPackage pkg = OPCPackage.open(file.toFile(), PackageAccess.READ)) {
            var reader = new XSSFReader(pkg);
            var strings = new ReadOnlySharedStringsTable(pkg);
            StylesTable styles = reader.getStylesTable();
            var units = new ExtractedUnitCollector(limits);
            for (SheetEntry sheet : sheets(reader)) {
                ExtractedUnitCollector.checkInterrupted();
                if (sheet.state() != null && !"visible".equals(sheet.state())) continue;
                String label = sheet.name() == null ? null : sheet.name().length() > 255 ? sheet.name().substring(0, 255) : sheet.name();
                units.begin(DocumentUnitType.SHEET, label);
                var rows = new RowCollector(units);
                try (InputStream data = reader.getSheet(sheet.relationId())) {
                    XMLReader parser = XMLHelper.newXMLReader();
                    parser.setContentHandler(new XSSFSheetXMLHandler(styles, null, strings, rows, new DataFormatter(Locale.US), false));
                    parser.parse(new InputSource(data));
                }
            }
            return units.finish();
        } catch (SAXException | OpenXML4JException | XMLStreamException | ParserConfigurationException e) {
            throw new IOException("invalid workbook", e);
        }
    }

    /** Sheet names, states and relation ids in workbook order, read with a hardened StAX parser (no DTD, no entities). */
    static List<SheetEntry> sheets(XSSFReader reader) throws IOException, OpenXML4JException, XMLStreamException {
        XMLInputFactory factory = XMLHelper.newXMLInputFactory();
        var sheets = new ArrayList<SheetEntry>();
        try (InputStream workbook = reader.getWorkbookData()) {
            XMLStreamReader xml = factory.createXMLStreamReader(workbook);
            try {
                while (xml.hasNext()) {
                    if (xml.next() == XMLStreamConstants.START_ELEMENT && "sheet".equals(xml.getLocalName()))
                        sheets.add(new SheetEntry(xml.getAttributeValue(null, "name"), xml.getAttributeValue(null, "state"),
                                xml.getAttributeValue(RELATIONSHIPS, "id")));
                }
            } finally {
                xml.close();
            }
        }
        return sheets;
    }

    private static final class RowCollector implements SheetContentsHandler {
        final ExtractedUnitCollector units;
        final TreeMap<Integer, String> cells = new TreeMap<>();

        RowCollector(ExtractedUnitCollector units) { this.units = units; }

        @Override public void startRow(int rowNum) { cells.clear(); }

        @Override
        public void endRow(int rowNum) {
            ExtractedUnitCollector.checkInterrupted();
            if (cells.values().stream().allMatch(v -> v == null || v.isEmpty())) return;
            int last = cells.lastKey();
            var line = new StringJoiner(" | ");
            for (int column = 0; column <= last; column++) line.add(cells.getOrDefault(column, ""));
            units.line(line.toString().replace('\n', ' ').replace('\r', ' '));
        }

        @Override
        public void cell(String reference, String formattedValue, XSSFComment comment) {
            if (reference == null || formattedValue == null || formattedValue.isEmpty()) return;
            cells.put((int) new CellReference(reference).getCol(), formattedValue);
        }
    }
}
