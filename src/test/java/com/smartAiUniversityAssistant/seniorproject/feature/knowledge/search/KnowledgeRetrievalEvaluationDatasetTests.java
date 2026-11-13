package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.search;

import com.smartAiUniversityAssistant.seniorproject.config.KnowledgeChunkingProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.DocumentUnitType;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.search.KnowledgeRetrievalEvaluationDataset.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl.ExtractTextStage;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/** Spec file 52, section 18.2: runs in every build, calls no provider. */
class KnowledgeRetrievalEvaluationDatasetTests {

    static List<ChunkDraft> sampleChunks() throws Exception {
        return sampleChunks(Document.REGULATIONS);
    }

    static List<ChunkDraft> sampleChunks(Document document) throws Exception {
        String raw = ExtractTextStage.sanitize(Files.readString(Path.of(document.path)).stripTrailing());
        String cleaned = new KnowledgeTextCleaner(true, true, true).clean(DocumentUnitType.TEXT, 1, raw, KnowledgeTextCleaner.RepeatedLines.none()).text();
        var chunks = new ArrayList<ChunkDraft>();
        var chunker = new KnowledgeTextChunker(new KnowledgeChunkingProperties(1000, 1500, 200, 150, 5000), chunks::add);
        chunker.unit(1, DocumentUnitType.TEXT, null, cleaned);
        chunker.finish();
        return chunks;
    }

    @Test
    void casesAreWellFormed() throws Exception {
        var cases = KnowledgeRetrievalEvaluationDataset.CASES;
        assertThat(cases.stream().map(Case::caseId).collect(Collectors.toSet())).hasSize(cases.size());
        for (Case c : cases) {
            if (c.expectation() == Expectation.FOUND) assertThat(c.mustContain()).as(c.caseId()).isNotBlank();
            else assertThat(c.mustContain()).as(c.caseId()).isNull();
        }
        assertThat(sampleChunks()).hasSize(6);
        assertThat(sampleChunks(Document.CURRICULUM)).hasSize(8);
        for (Case c : cases.stream().filter(c -> c.expectation() == Expectation.FOUND).toList())
            assertThat(sampleChunks(c.document()).stream().filter(chunk -> chunk.content().contains(c.mustContain())).count())
                    .as(c.caseId() + " " + c.mustContain()).isOne();
        assertThat(cases.stream().filter(c -> c.document() == Document.CURRICULUM).count()).isEqualTo(10);
        assertThat(cases.stream().filter(c -> c.expectation() == Expectation.NOT_FOUND).count()).isGreaterThanOrEqualTo(5);
        var groups = cases.stream().collect(Collectors.groupingBy(Case::group, Collectors.counting()));
        for (String group : List.of("EN", "TH", "X")) assertThat(groups.get(group)).as(group).isGreaterThanOrEqualTo(2);
    }
}
