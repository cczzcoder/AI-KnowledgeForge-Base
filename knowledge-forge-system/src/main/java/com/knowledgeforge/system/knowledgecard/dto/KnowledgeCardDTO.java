package com.knowledgeforge.system.knowledgecard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeCardDTO {

    private UUID id;
    private UUID kbId;
    private UUID conversationId;
    private UUID messageId;
    private String title;
    private String content;
    private String category;
    private String status;
    private String entityType;
    private String sourceContext;
    private String reviewerNote;
    private Boolean vectorized;
    private UUID graphEntityId;
    private LocalDateTime createdAt;

    /**
     * 对话知识提取请求
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtractionRequest {
        /** 知识库ID */
        private UUID kbId;
        /** 会话ID */
        private UUID conversationId;
        /** 可选：最多提取条数 */
        @Builder.Default
        private int maxCards = 5;
    }

    /**
     * 提取结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtractionResult {
        /** 提取的知识卡片列表 */
        private List<KnowledgeCardDTO> cards;
        /** 分析的会话消息数量 */
        private int messagesAnalyzed;
        /** 提取摘要 */
        private String summary;
    }

    /**
     * 审核请求
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewRequest {
        /** 审核操作：APPROVED / REJECTED */
        private String action;
        /** 审核备注 */
        private String note;
        /** 可选：审核时修改分类 */
        private String category;
        /** 可选：审核时修改实体类型 */
        private String entityType;
    }

    /**
     * 卡片列表分页结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CardPageResult {
        private List<KnowledgeCardDTO> items;
        private long total;
        private int page;
        private int size;
    }

    /**
     * 批量审核请求
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchReviewRequest {
        private UUID kbId;
        private List<UUID> cardIds;
        private ReviewRequest review;
    }
}