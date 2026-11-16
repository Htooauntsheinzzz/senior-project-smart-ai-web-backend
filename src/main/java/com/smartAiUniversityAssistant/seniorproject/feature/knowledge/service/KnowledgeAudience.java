package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service;

/**
 * Step 15B (spec file 54, section 6): whom a passage applies to, seen from the Student scope of the search. PROGRAM: the
 * document or its curriculum version belongs to the Student's program; DEPARTMENT: the document has a department;
 * UNIVERSITY: otherwise. A search without a Student scope has no audience (null).
 */
public enum KnowledgeAudience { PROGRAM, DEPARTMENT, UNIVERSITY }
