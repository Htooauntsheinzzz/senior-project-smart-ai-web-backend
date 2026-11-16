package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.ReprocessMode;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.exception.KnowledgeFailure;
import java.util.*;
import tools.jackson.databind.JsonNode;

/**
 * Optional body of POST /documents/{id}/reprocess. No body, {} or a null mode means RESUME. Parsed strictly from the
 * JSON tree: the body must be an object whose only property is mode, and mode must be the text RESUME or FULL (a
 * number, another value, a non-object body or an unknown property is 400 VALIDATION_ERROR).
 */
public record KnowledgeDocumentReprocessRequest(ReprocessMode mode) {
    public static KnowledgeDocumentReprocessRequest from(JsonNode body) {
        if (body == null || body.isMissingNode() || body.isNull()) return new KnowledgeDocumentReprocessRequest(null);
        if (!body.isObject()) throw KnowledgeFailure.invalid();
        var names = new ArrayList<String>();
        body.propertyNames().forEach(names::add);
        if (!Set.of("mode").containsAll(names)) throw KnowledgeFailure.invalid();
        JsonNode mode = body.get("mode");
        if (mode == null || mode.isNull()) return new KnowledgeDocumentReprocessRequest(null);
        if (!mode.isString()) throw KnowledgeFailure.invalid();
        return switch (mode.asString()) {
            case "RESUME" -> new KnowledgeDocumentReprocessRequest(ReprocessMode.RESUME);
            case "FULL" -> new KnowledgeDocumentReprocessRequest(ReprocessMode.FULL);
            default -> throw KnowledgeFailure.invalid();
        };
    }

    public ReprocessMode effectiveMode() { return mode == null ? ReprocessMode.RESUME : mode; }
}
