package com.smartAiUniversityAssistant.seniorproject.feature.academicresult.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.academicresult.entity.GradeDefinition;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface GradeDefinitionRepository extends JpaRepository<GradeDefinition, Long> {
    @EntityGraph(attributePaths = "gradingScheme")
    Optional<GradeDefinition> findWithSchemeById(Long id);
}
