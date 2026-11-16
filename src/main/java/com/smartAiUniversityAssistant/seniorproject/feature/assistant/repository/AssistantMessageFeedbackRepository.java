package com.smartAiUniversityAssistant.seniorproject.feature.assistant.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.entity.AssistantMessageFeedback;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spec file 59: feedback rows. The Admin list and its filters are read by {@link AdminAssistantQueryRepository}. */
public interface AssistantMessageFeedbackRepository extends JpaRepository<AssistantMessageFeedback, Long> {
    Optional<AssistantMessageFeedback> findByMessageId(Long messageId);

    List<AssistantMessageFeedback> findByMessageIdIn(Collection<Long> messageIds);

    /** The feedback of a conversation the Student deletes; its messages and the conversation follow in the same transaction. */
    @Modifying
    @Query("delete from AssistantMessageFeedback f where f.conversationId = :conversationId")
    int deleteByConversationId(@Param("conversationId") UUID conversationId);
}
