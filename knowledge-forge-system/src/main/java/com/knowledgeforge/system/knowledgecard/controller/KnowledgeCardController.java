package com.knowledgeforge.system.knowledgecard.controller;

import com.knowledgeforge.core.shared.constant.SystemConstants;
import com.knowledgeforge.core.shared.dto.ApiResponse;
import com.knowledgeforge.system.knowledgecard.dto.KnowledgeCardDTO;
import com.knowledgeforge.system.knowledgecard.service.KnowledgeCardService;
import com.knowledgeforge.system.knowledgecard.service.KnowledgeCardSyncService;
import com.knowledgeforge.system.knowledgecard.service.KnowledgeExtractionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 知识卡片控制器 — 对话知识提取 + 卡片查询 + 审核管理
 */
@RestController
@RequestMapping(SystemConstants.API_V1 + "/knowledge-cards")
@RequiredArgsConstructor
public class KnowledgeCardController {

    private final KnowledgeExtractionService extractionService;
    private final KnowledgeCardService knowledgeCardService;
    private final KnowledgeCardSyncService syncService;

    // ==================== 知识提取 ====================

    /**
     * 从对话中提取知识卡片
     * POST /api/v1/knowledge-cards/extract
     */
    @PostMapping("/extract")
    public ApiResponse<KnowledgeCardDTO.ExtractionResult> extractFromConversation(
            @Valid @RequestBody KnowledgeCardDTO.ExtractionRequest request) {
        KnowledgeCardDTO.ExtractionResult result = extractionService.extractFromConversation(
                request.getKbId(), request.getConversationId(), request.getMaxCards());
        return ApiResponse.success(result);
    }

    // ==================== 卡片查询 ====================

    /**
     * 分页查询知识卡片列表
     * GET /api/v1/knowledge-cards?kbId=xxx&status=PENDING&page=0&size=10
     */
    @GetMapping
    public ApiResponse<KnowledgeCardDTO.CardPageResult> listCards(
            @RequestParam UUID kbId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        KnowledgeCardDTO.CardPageResult result = knowledgeCardService.listCards(kbId, status, page, size);
        return ApiResponse.success(result);
    }

    /**
     * 获取待审核卡片数量
     * GET /api/v1/knowledge-cards/pending-count?kbId=xxx
     */
    @GetMapping("/pending-count")
    public ApiResponse<Map<String, Long>> pendingCount(@RequestParam UUID kbId) {
        long count = knowledgeCardService.countPending(kbId);
        return ApiResponse.success(Map.of("count", count));
    }

    // ==================== 审核操作 ====================

    /**
     * 审核单张知识卡片
     * PUT /api/v1/knowledge-cards/{id}/review
     */
    @PutMapping("/{id}/review")
    public ApiResponse<KnowledgeCardDTO> reviewCard(
            @PathVariable UUID id,
            @Valid @RequestBody KnowledgeCardDTO.ReviewRequest request) {
        KnowledgeCardDTO result = knowledgeCardService.reviewCard(id, request);
        return ApiResponse.success(result);
    }

    /**
     * 批量审核知识卡片
     * POST /api/v1/knowledge-cards/batch-review
     */
    @PostMapping("/batch-review")
    public ApiResponse<Map<String, Integer>> batchReview(
            @RequestBody KnowledgeCardDTO.BatchReviewRequest batchRequest) {
        int count = knowledgeCardService.batchReview(
                batchRequest.getKbId(), batchRequest.getCardIds(), batchRequest.getReview());
        return ApiResponse.success(Map.of("reviewed", count));
    }

    // ==================== 删除 ====================

    /**
     * 删除知识卡片
     * DELETE /api/v1/knowledge-cards/{id}
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteCard(@PathVariable UUID id) {
        knowledgeCardService.deleteCard(id);
        return ApiResponse.success(null);
    }

    // ==================== 同步 ====================

    /**
     * 重试失败的同步任务（已通过但未向量化的卡片）
     * POST /api/v1/knowledge-cards/retry-sync?kbId=xxx
     */
    @PostMapping("/retry-sync")
    public ApiResponse<Map<String, Integer>> retrySync(@RequestParam UUID kbId) {
        int count = syncService.retryFailedSync(kbId);
        return ApiResponse.success(Map.of("synced", count));
    }
}