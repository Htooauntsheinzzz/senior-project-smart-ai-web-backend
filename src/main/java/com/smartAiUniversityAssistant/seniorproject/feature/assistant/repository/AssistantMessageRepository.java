package com.smartAiUniversityAssistant.seniorproject.feature.assistant.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.entity.AssistantMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Step 17: stored messages. Both queries return the newest first; callers reverse a window into reading order. */
public interface AssistantMessageRepository extends JpaRepository<AssistantMessage, Long> {
    @Query("select m from AssistantMessage m where m.conversationId = :conversationId order by m.id desc")
    List<AssistantMessage> findNewest(@Param("conversationId") UUID conversationId, Pageable limit);

    @Query("select m from AssistantMessage m where m.conversationId = :conversationId and m.id < :beforeId order by m.id desc")
    List<AssistantMessage> findNewestBefore(@Param("conversationId") UUID conversationId, @Param("beforeId") long beforeId,
            Pageable limit);

    /** Every message of a conversation the Student deletes; the conversation row follows in the same transaction. */
    @Modifying
    @Query("delete from AssistantMessage m where m.conversationId = :conversationId")
    int deleteByConversationId(@Param("conversationId") UUID conversationId);
}
