package com.knowledgeforge.system.knowledgecard.service;

import com.knowledgeforge.core.entity.KnowledgeCard;
import com.knowledgeforge.core.entity.KnowledgeGraphEntity;
import com.knowledgeforge.system.graph.repository.KnowledgeGraphEntityRepository;
import com.knowledgeforge.system.knowledgecard.repository.KnowledgeCardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 知识卡片三路同步服务 — 审核通过后将卡片内容同步写入向量库和图谱。
 *
 * 三路写入：
 *  1. 知识卡片表 (knowledge_card) — 主存储，已完成
 *  2. 向量库 (pgvector) — 用于RAG语义检索
 *  3. 知识图谱 (knowledge_graph_entity) — 用于结构化关联分析
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeCardSyncService {

    private final KnowledgeCardRepository cardRepository;
    private final KnowledgeGraphEntityRepository entityRepository;
    private final PgVectorStore vectorStore;

    /**
     * 审核通过后同步写入向量库和图谱。
     * 该方法在 KnowledgeCardService.reviewCard 内部调用，共享事务。
     *
     * @param card 已通过审核的知识卡片（status=APPROVED）
     */
    @Transactional
    public void syncApprovedCard(KnowledgeCard card) {
        if (!"APPROVED".equals(card.getStatus())) {
            return;
        }
        if (Boolean.TRUE.equals(card.getVectorized())) {
            log.debug("卡片已向量化，跳过: id={}", card.getId());
            return;
        }

        try {
            // 1. 向量化写入 pgvector
            syncToVectorStore(card);

            // 2. 写入知识图谱
            syncToGraph(card);

            // 3. 标记已完成
            card.setVectorized(true);
            cardRepository.save(card);
            log.info("知识卡片三路同步完成: id={}, title={}", card.getId(), card.getTitle());
        } catch (Exception e) {
            log.error("知识卡片同步失败: id={}, error={}", card.getId(), e.getMessage());
            // 同步失败不阻塞审核流程，卡片仍保持 APPROVED 状态，后续可重试
        }
    }

    /**
     * 将卡片内容向量化并写入 pgvector 向量库。
     */
    private void syncToVectorStore(KnowledgeCard card) {
        Document springDoc = new Document(card.getTitle() + "\n" + card.getContent());
        springDoc.getMetadata().put("chunk_id", card.getId().toString());
        springDoc.getMetadata().put("document_id", card.getId().toString());
        springDoc.getMetadata().put("kb_id", card.getKbId().toString());
        springDoc.getMetadata().put("chunk_index", 0);
        springDoc.getMetadata().put("source_type", "knowledge_card");
        springDoc.getMetadata().put("card_category", card.getCategory());
        springDoc.getMetadata().put("card_title", card.getTitle());

        vectorStore.add(List.of(springDoc));
        log.debug("卡片向量化完成: id={}, title={}", card.getId(), card.getTitle());
    }

    /**
     * 在知识图谱中创建对应实体节点。
     * 如果卡片已指定 entityType，则使用；否则根据 category 推断。
     */
    private void syncToGraph(KnowledgeCard card) {
        String entityType = inferEntityType(card);
        String graphName = card.getTitle();

        // 检查是否已存在同名实体（避免重复创建）
        List<KnowledgeGraphEntity> existing = entityRepository.findByNameExact(card.getKbId(), graphName);
        if (!existing.isEmpty()) {
            card.setGraphEntityId(existing.get(0).getId());
            log.debug("图谱实体已存在，复用: name={}, id={}", graphName, existing.get(0).getId());
            return;
        }

        KnowledgeGraphEntity entity = KnowledgeGraphEntity.builder()
                .kbId(card.getKbId())
                .name(graphName)
                .entityType(entityType)
                .description(card.getContent())
                .build();
        KnowledgeGraphEntity savedEntity = entityRepository.save(entity);
        card.setGraphEntityId(savedEntity.getId());
        log.debug("图谱实体创建完成: name={}, type={}, id={}", graphName, entityType, savedEntity.getId());
    }

    /**
     * 推断图谱实体类型：优先使用卡片指定的 entityType，
     * 否则根据 category 映射。
     */
    private String inferEntityType(KnowledgeCard card) {
        if (card.getEntityType() != null && !card.getEntityType().isBlank()) {
            return card.getEntityType();
        }
        return switch (card.getCategory()) {
            case "CONCEPT" -> "概念";
            case "FACT" -> "事件";
            case "RULE" -> "规则";
            case "INSIGHT" -> "观点";
            default -> "概念";
        };
    }

    /**
     * 重试失败的同步任务：查询指定知识库中已通过但未向量化的卡片并重新同步。
     */
    @Transactional
    public int retryFailedSync(UUID kbId) {
        List<KnowledgeCard> cards = cardRepository.findApprovedNotVectorized(kbId);
        int count = 0;
        for (KnowledgeCard card : cards) {
            try {
                syncApprovedCard(card);
                count++;
            } catch (Exception e) {
                log.warn("重试同步失败: id={}, error={}", card.getId(), e.getMessage());
            }
        }
        log.info("重试同步完成: kbId={}, 成功{}/{}", kbId, count, cards.size());
        return count;
    }
}