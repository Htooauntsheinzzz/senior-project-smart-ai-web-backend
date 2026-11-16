package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto.KnowledgeIndexStatisticsResponse;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;

public interface KnowledgeIndexService {
    KnowledgeIndexStatisticsResponse statistics(AuthenticatedUser actor);
}
