package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service;

/** Chunk counts of one file for the INDEX stage (spec file 51, section 4). pageCount is MAX(page_number), 0 without pages. */
public record FileIndexCounts(long chunks, long embeddedWithModel, int maxChunkIndex, int maxPageEnd, int pageCount) {}
