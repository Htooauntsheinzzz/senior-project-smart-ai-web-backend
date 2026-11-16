package com.smartAiUniversityAssistant.seniorproject.feature.assistant.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.entity.AssistantConversation;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

/** Step 17: conversations in PostgreSQL. {@link AssistantConversationRepository} remains the Redis context store. */
public interface AssistantConversationHistoryRepository extends JpaRepository<AssistantConversation, UUID> {
    Optional<AssistantConversation> findByIdAndStudentId(UUID id, Long studentId);

    Page<AssistantConversation> findByStudentId(Long studentId, Pageable pageable);

    // Serializes the turns and the deletion of one conversation.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select c from AssistantConversation c where c.id = :id")
    Optional<AssistantConversation> findByIdForUpdate(@Param("id") UUID id);
}
