package com.smartAiUniversityAssistant.seniorproject.feature.campus.service.impl;

import jakarta.persistence.criteria.*;
import java.util.Locale;

final class CampusQuerySupport {
    private CampusQuerySupport() {}
    static Predicate contains(CriteriaBuilder cb, Expression<String> field, String term) {
        String literal = term.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return cb.like(cb.lower(field), "%" + literal + "%", '\\');
    }
}
