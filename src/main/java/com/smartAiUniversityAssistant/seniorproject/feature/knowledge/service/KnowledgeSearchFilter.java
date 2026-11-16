package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service;

/**
 * studentScope null = no audience restriction (Admin view). The other values may each be null. programDocumentsOnly
 * (Step 15B) limits the search to documents of one program: its own link or a curriculum version of the program.
 */
public record KnowledgeSearchFilter(StudentKnowledgeScope studentScope, Long categoryId, String academicYearLabel,
        Long documentId, Long programDocumentsOnly) {

    /** Spec file 52 call sites: no program limit. */
    public KnowledgeSearchFilter(StudentKnowledgeScope studentScope, Long categoryId, String academicYearLabel, Long documentId) {
        this(studentScope, categoryId, academicYearLabel, documentId, null);
    }

    /** The same filter, limited to documents of this program: its own link or a curriculum version of the program. */
    public KnowledgeSearchFilter programDocuments(long programId) {
        return new KnowledgeSearchFilter(studentScope, categoryId, academicYearLabel, documentId, programId);
    }
}
