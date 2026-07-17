package com.knowledgeforge.system.conversation.repository;

import com.knowledgeforge.core.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Page<Conversation> findByDeletedFalseOrderByUpdatedAtDesc(Pageable pageable);

    Optional<Conversation> findByIdAndDeletedFalse(UUID id);

    List<Conversation> findByKbId(UUID kbId);

    List<Conversation> findByDeletedFalseAndUpdatedAtBefore(LocalDateTime threshold);

    @Modifying
    @Query("DELETE FROM Conversation c WHERE c.kbId = :kbId")
    void deleteByKbId(@Param("kbId") UUID kbId);
}
