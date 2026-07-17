package com.knowledgeforge.system.conversation.repository;

import com.knowledgeforge.core.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    List<ChatMessage> findByConversationIdInOrderByCreatedAtAsc(Collection<UUID> conversationIds);

    @Modifying
    @Query("DELETE FROM ChatMessage m WHERE m.conversationId = :conversationId")
    void deleteByConversationId(@Param("conversationId") UUID conversationId);

    @Modifying
    @Query("DELETE FROM ChatMessage m WHERE m.conversationId IN :conversationIds")
    void deleteByConversationIdIn(@Param("conversationIds") Collection<UUID> conversationIds);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.feedback = :feedback")
    long countByFeedback(@Param("feedback") String feedback);
}