package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import java.util.List;

/** Semantic search over the indexed chunks (spec file 52). */
public interface KnowledgeSearchService {
    /** Search for an Admin: every eligible document, or the view of a Student with the given scope. */
    KnowledgeSearchResponse adminSearch(AuthenticatedUser actor, KnowledgeSearchRequest request);

    /** Search for the signed-in Student. Used by the assistant tool of spec file 53. No controller calls it. */
    List<KnowledgeSearchHit> studentSearch(String query, StudentKnowledgeScope scope, int topK);

    /** Search for the signed-in Student with the options of spec file 54. Used by the assistant tool. */
    List<KnowledgeSearchHit> studentSearch(String query, StudentKnowledgeScope scope, StudentSearchOptions options);
}
