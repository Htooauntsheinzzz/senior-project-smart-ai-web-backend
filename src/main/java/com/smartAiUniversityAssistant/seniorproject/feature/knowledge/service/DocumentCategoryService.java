package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;

public interface DocumentCategoryService {
    DocumentCategoryPageResponse list(AuthenticatedUser actor, DocumentCategoryListQuery query);
    DocumentCategoryResponse detail(AuthenticatedUser actor, long id);
    DocumentCategoryResponse create(AuthenticatedUser actor, DocumentCategoryRequest request);
    DocumentCategoryResponse update(AuthenticatedUser actor, long id, DocumentCategoryRequest request);
    DocumentCategoryResponse activate(AuthenticatedUser actor, long id);
    DocumentCategoryResponse deactivate(AuthenticatedUser actor, long id);
    void delete(AuthenticatedUser actor, long id);
}
