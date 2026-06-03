package com.knowledgeforge.system.knowledgecard.repository;

import com.knowledgeforge.core.entity.KnowledgeCard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface KnowledgeCardRepository extends JpaRepository<KnowledgeCard, UUID> {

    /** 按知识库和状态分页查询 */
    Page<KnowledgeCard> findByKbIdAndStatus(UUID kbId, String status, Pageable pageable);

    /** 按知识库查询所有卡片 */
    Page<KnowledgeCard> findByKbId(UUID kbId, Pageable pageable);

    /** 按会话查询提取的卡片 */
    List<KnowledgeCard> findByConversationId(UUID conversationId);

    /** 统计知识库中待审核卡片数量 */
    @Query("SELECT COUNT(kc) FROM KnowledgeCard kc WHERE kc.kbId = :kbId AND kc.status = 'PENDING'")
    long countPendingByKbId(@Param("kbId") UUID kbId);

    /** 查询已通过审核但未向量化的卡片 */
    @Query("SELECT kc FROM KnowledgeCard kc WHERE kc.kbId = :kbId AND kc.status = 'APPROVED' AND kc.vectorized = false")
    List<KnowledgeCard> findApprovedNotVectorized(@Param("kbId") UUID kbId);

    /** 按知识库和分类查询已通过的卡片 */
    @Query("SELECT kc FROM KnowledgeCard kc WHERE kc.kbId = :kbId AND kc.status = 'APPROVED' AND kc.category = :category")
    List<KnowledgeCard> findApprovedByCategory(@Param("kbId") UUID kbId, @Param("category") String category);
}