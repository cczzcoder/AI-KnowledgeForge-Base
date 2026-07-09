package com.knowledgeforge.system.knowledgecard.service;

import com.knowledgeforge.core.entity.KnowledgeCard;
import com.knowledgeforge.system.knowledgecard.dto.KnowledgeCardDTO;
import com.knowledgeforge.system.knowledgecard.repository.KnowledgeCardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 知识卡片管理服务 — 查询、审核（通过/驳回）、删除。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeCardService {

    private final KnowledgeCardRepository knowledgeCardRepository;
    private final KnowledgeCardSyncService syncService;

    /**
     * 分页查询知识卡片列表
     *
     * @param kbId   知识库ID
     * @param status 筛选状态（可选：PENDING/APPROVED/REJECTED，为null时返回全部）
     * @param page   页码（0-based）
     * @param size   每页条数
     */
    private static final java.util.Set<String> VALID_STATUSES =
            java.util.Set.of("PENDING", "APPROVED", "REJECTED");

    @Transactional(readOnly = true)
    public KnowledgeCardDTO.CardPageResult listCards(UUID kbId, String status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<KnowledgeCard> cardPage;
        if (status != null && !status.isBlank()) {
            String upperStatus = status.toUpperCase();
            if (!VALID_STATUSES.contains(upperStatus)) {
                throw new IllegalArgumentException(
                        "状态值无效: " + status + "，有效值为 PENDING / APPROVED / REJECTED");
            }
            cardPage = knowledgeCardRepository.findByKbIdAndStatus(kbId, upperStatus, pageRequest);
        } else {
            cardPage = knowledgeCardRepository.findByKbId(kbId, pageRequest);
        }

        var items = cardPage.getContent().stream().map(this::toDTO).toList();
        return KnowledgeCardDTO.CardPageResult.builder()
                .items(items)
                .total(cardPage.getTotalElements())
                .page(page)
                .size(size)
                .build();
    }

    /**
     * 审核知识卡片 — 通过或驳回
     *
     * @param cardId  卡片ID
     * @param request 审核请求（action: APPROVED/REJECTED, note, category, entityType）
     * @return 更新后的卡片
     */
    @Transactional
    public KnowledgeCardDTO reviewCard(UUID cardId, KnowledgeCardDTO.ReviewRequest request) {
        KnowledgeCard card = knowledgeCardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("知识卡片不存在: " + cardId));

        String action = request.getAction();
        if (!"APPROVED".equals(action) && !"REJECTED".equals(action)) {
            throw new IllegalArgumentException("审核操作无效: " + action + "，有效值为 APPROVED 或 REJECTED");
        }

        card.setStatus(action);
        if (request.getNote() != null && !request.getNote().isBlank()) {
            card.setReviewerNote(request.getNote());
        }
        if (request.getCategory() != null && !request.getCategory().isBlank()) {
            card.setCategory(normalizeCategory(request.getCategory()));
        }
        if (request.getEntityType() != null && !request.getEntityType().isBlank()) {
            card.setEntityType(request.getEntityType());
        }

        KnowledgeCard updated = knowledgeCardRepository.save(card);
        log.info("知识卡片审核完成: id={}, status={}", cardId, action);

        // 审核通过后同步写入向量库和图谱
        if ("APPROVED".equals(action)) {
            syncService.syncApprovedCard(updated);
        }

        return toDTO(updated);
    }

    /**
     * 批量审核 — 带知识库归属校验，防止跨知识库越权操作。
     * 优化：批量查询+批量保存，消除 N+1 问题。
     *
     * @param kbId    知识库ID
     * @param cardIds 待审核的卡片ID列表
     * @param request 审核请求（action, note, category, entityType）
     * @return 成功审核的卡片数量
     */
    @Transactional
    public int batchReview(UUID kbId, List<UUID> cardIds, KnowledgeCardDTO.ReviewRequest request) {
        // 1. 批量加载所有卡片（1次查询，消除 N+1）
        List<KnowledgeCard> cards = knowledgeCardRepository.findAllById(cardIds);
        Set<UUID> foundIds = cards.stream().map(KnowledgeCard::getId).collect(Collectors.toSet());
        for (UUID cardId : cardIds) {
            if (!foundIds.contains(cardId)) {
                log.warn("批量审核：卡片不存在: id={}", cardId);
            }
        }

        // 2. 内存中校验归属 + 更新状态
        String action = request.getAction();
        if (!"APPROVED".equals(action) && !"REJECTED".equals(action)) {
            throw new IllegalArgumentException("审核操作无效: " + action + "，有效值为 APPROVED 或 REJECTED");
        }
        List<KnowledgeCard> approvedCards = new ArrayList<>();
        int count = 0;

        for (KnowledgeCard card : cards) {
            if (!card.getKbId().equals(kbId)) {
                log.warn("跨知识库审核拒绝: cardId={}, cardKbId={}, requestKbId={}",
                        card.getId(), card.getKbId(), kbId);
                continue;
            }

            card.setStatus(action);
            if (request.getNote() != null && !request.getNote().isBlank()) {
                card.setReviewerNote(request.getNote());
            }
            if (request.getCategory() != null && !request.getCategory().isBlank()) {
                card.setCategory(normalizeCategory(request.getCategory()));
            }
            if (request.getEntityType() != null && !request.getEntityType().isBlank()) {
                card.setEntityType(request.getEntityType());
            }

            if ("APPROVED".equals(action)) {
                approvedCards.add(card);
            }
            count++;
        }

        // 3. 批量保存（1次写入，替代 N 次 save）
        if (!cards.isEmpty()) {
            knowledgeCardRepository.saveAll(cards);
        }

        // 4. 批量同步向量库和图谱（仅审核通过的卡片）
        for (KnowledgeCard card : approvedCards) {
            try {
                syncService.syncApprovedCard(card);
            } catch (Exception e) {
                log.warn("批量同步卡片失败: id={}, error={}", card.getId(), e.getMessage());
            }
        }

        log.info("批量审核完成: kbId={}, 成功{}/{}", kbId, count, cardIds.size());
        return count;
    }

    /**
     * 获取待审核卡片数量
     */
    @Transactional(readOnly = true)
    public long countPending(UUID kbId) {
        return knowledgeCardRepository.countPendingByKbId(kbId);
    }

    /**
     * 删除知识卡片
     */
    @Transactional
    public void deleteCard(UUID cardId) {
        if (!knowledgeCardRepository.existsById(cardId)) {
            throw new IllegalArgumentException("知识卡片不存在: " + cardId);
        }
        knowledgeCardRepository.deleteById(cardId);
        log.info("知识卡片已删除: id={}", cardId);
    }

    private String normalizeCategory(String raw) {
        if (raw == null) return "CONCEPT";
        return switch (raw.toUpperCase()) {
            case "FACT" -> "FACT";
            case "RULE" -> "RULE";
            case "INSIGHT" -> "INSIGHT";
            default -> "CONCEPT";
        };
    }

    private KnowledgeCardDTO toDTO(KnowledgeCard card) {
        return KnowledgeCardDTO.builder()
                .id(card.getId())
                .kbId(card.getKbId())
                .conversationId(card.getConversationId())
                .messageId(card.getMessageId())
                .title(card.getTitle())
                .content(card.getContent())
                .category(card.getCategory())
                .status(card.getStatus())
                .entityType(card.getEntityType())
                .sourceContext(card.getSourceContext())
                .reviewerNote(card.getReviewerNote())
                .vectorized(card.getVectorized())
                .graphEntityId(card.getGraphEntityId())
                .createdAt(card.getCreatedAt())
                .build();
    }
}