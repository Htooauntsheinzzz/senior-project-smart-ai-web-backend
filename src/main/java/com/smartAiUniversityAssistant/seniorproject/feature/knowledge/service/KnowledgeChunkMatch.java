package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service;

/**
 * One row of the search statement (never the vector). distance is the cosine distance to the question.
 * curriculumProgramId (Step 15B) is the program of the document's curriculum version, null without one.
 */
public record KnowledgeChunkMatch(
        long chunkId, int chunkIndex, String sectionTitle, int pageStart, int pageEnd, String content,
        long fileId, int revisionNumber, String fileExtension,
        long documentId, String title, String academicYearLabel,
        Long categoryId, String categoryCode, String categoryName,
        Long curriculumVersionId, Long programId, Long departmentId,
        double distance, Long curriculumProgramId) {

    /** Spec file 52 call sites: no curriculum program. */
    public KnowledgeChunkMatch(long chunkId, int chunkIndex, String sectionTitle, int pageStart, int pageEnd, String content,
            long fileId, int revisionNumber, String fileExtension, long documentId, String title, String academicYearLabel,
            Long categoryId, String categoryCode, String categoryName, Long curriculumVersionId, Long programId, Long departmentId,
            double distance) {
        this(chunkId, chunkIndex, sectionTitle, pageStart, pageEnd, content, fileId, revisionNumber, fileExtension, documentId,
                title, academicYearLabel, categoryId, categoryCode, categoryName, curriculumVersionId, programId, departmentId,
                distance, null);
    }
}
