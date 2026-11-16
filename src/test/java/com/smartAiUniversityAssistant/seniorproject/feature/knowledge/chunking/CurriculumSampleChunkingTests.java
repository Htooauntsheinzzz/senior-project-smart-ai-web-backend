package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.chunking;

import static com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.DocumentUnitType.TEXT;
import static org.assertj.core.api.Assertions.assertThat;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.ChunkDraft;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.KnowledgeTextCleaner;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl.ExtractTextStage;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

/** Spec file 54, section 13.1: the sample curriculum gives exactly eight chunks with the default configuration. */
class CurriculumSampleChunkingTests {

    @Test
    void workedExampleOfSpec54Section13_1() throws Exception {
        String raw = ExtractTextStage.sanitize(Files.readString(Path.of("docker/dev-fixtures/rag-sample-curriculum-cs.txt")).stripTrailing());
        String cleaned = new KnowledgeTextCleaner(true, true, true).clean(TEXT, 1, raw, KnowledgeTextCleaner.RepeatedLines.none()).text();
        var chunks = KnowledgeTextChunkerTests.chunk(cleaned);
        System.out.println("CURRICULUM_CHUNKS " + chunks.stream().map(c -> c.sectionTitle() + "=" + c.charCount()).toList());
        assertThat(chunks).extracting(ChunkDraft::sectionTitle).containsExactly("Section 1 Program Overview",
                "Section 2 Curriculum Structure", "Section 3 Major Required Courses", "Section 4 Major Elective Courses",
                "Section 4 Major Elective Courses", "Section 5 Cooperative Education and Internship", "Section 6 Graduation Requirements",
                "หมวด 7 สหกิจศึกษา (สรุปภาษาไทย)");
        assertThat(chunks).extracting(ChunkDraft::charCount).containsExactly(450, 409, 641, 974, 340, 634, 522, 273);
        assertThat(chunks.get(0).content()).startsWith("SAMPLE DATA FOR DEVELOPMENT ONLY");
        assertThat(chunks.get(4).content()).startsWith("Course code | Course title | Credits");
        for (int i : new int[] {1, 2, 3, 5, 6, 7}) assertThat(chunks.get(i).content()).startsWith(chunks.get(i).sectionTitle());
    }
}
