package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl;

import com.smartAiUniversityAssistant.seniorproject.config.KnowledgeExtractionProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.exception.KnowledgeIngestionException;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * PDF with Apache PDFBox 3 (spec file 47, section 8). One PAGE unit per page in order, empty pages included, so the
 * unit number is the page number a viewer shows. Only page content is read: bookmarks, annotations, form values,
 * attachments and metadata are ignored and nothing in the file is executed. A temporary-file stream cache keeps large
 * files out of the heap. No OCR: scanned pages come out empty.
 */
@Component
public class PdfTextExtractor implements DocumentTextExtractor {
    private static final String ENCRYPTED = "The document is password-protected or does not allow text extraction.";
    private final boolean sortByPosition;

    public PdfTextExtractor(KnowledgeExtractionProperties properties) {
        this.sortByPosition = properties.pdfSortByPosition();
    }

    @Override public boolean supports(String fileExtension) { return "pdf".equals(fileExtension); }

    @Override
    public List<ExtractedUnit> extract(Path file, ExtractionLimits limits) throws IOException {
        PDDocument document;
        try {
            document = Loader.loadPDF(file.toFile(), "", IOUtils.createTempFileOnlyStreamCache());
        } catch (InvalidPasswordException e) {
            throw new KnowledgeIngestionException(IngestionStageType.EXTRACT, "DOCUMENT_ENCRYPTED", false, ENCRYPTED);
        }
        try (document) {
            if (!document.getCurrentAccessPermission().canExtractContent())
                throw new KnowledgeIngestionException(IngestionStageType.EXTRACT, "DOCUMENT_ENCRYPTED", false, ENCRYPTED);
            int pages = document.getNumberOfPages();
            if (pages > limits.maxPages())
                throw new KnowledgeIngestionException(IngestionStageType.EXTRACT, ExtractedUnitCollector.TOO_MANY_PAGES, false,
                        "The document has more pages than the configured limit.");
            if (pages == 0)
                throw new KnowledgeIngestionException(IngestionStageType.EXTRACT, "DOCUMENT_TEXT_EMPTY", false,
                        "No text could be extracted. Scanned documents are not supported.");
            var stripper = new PDFTextStripper();
            stripper.setSortByPosition(sortByPosition);
            stripper.setLineSeparator("\n");
            var units = new ExtractedUnitCollector(limits);
            for (int page = 1; page <= pages; page++) {
                ExtractedUnitCollector.checkInterrupted();
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                units.whole(DocumentUnitType.PAGE, null, stripper.getText(document));
            }
            return units.finish();
        }
    }
}
