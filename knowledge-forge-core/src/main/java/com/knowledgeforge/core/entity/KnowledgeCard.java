package com.knowledgeforge.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 知识卡片 — 从对话中提取的可沉淀知识单元，需经审核流程方可入库。
 * 审核通过后同步写入向量库(pgvector)和知识图谱。
 */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "knowledge_card", indexes = {
        @Index(name = "idx_kc_kb_id", columnList = "kb_id"),
        @Index(name = "idx_kc_status", columnList = "status"),
        @Index(name = "idx_kc_category", columnList = "category"),
        @Index(name = "idx_kc_conversation_id", columnList = "conversation_id")
})
public class KnowledgeCard {

    /** 卡片状态枚举 */
    public enum Status {
        /** 待审核 */
        PENDING,
        /** 已通过 */
        APPROVED,
        /** 已驳回 */
        REJECTED
    }

    /** 知识分类枚举 */
    public enum Category {
        /** 概念定义 */
        CONCEPT,
        /** 事实陈述 */
        FACT,
        /** 规则/原理 */
        RULE,
        /** 洞察/观点 */
        INSIGHT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "kb_id", nullable = false)
    private UUID kbId;

    /** 来源会话ID */
    @Column(name = "conversation_id")
    private UUID conversationId;

    /** 来源消息ID */
    @Column(name = "message_id")
    private UUID messageId;

    /** 卡片标题 */
    @Column(nullable = false, length = 500)
    private String title;

    /** 卡片正文 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 知识分类 */
    @Column(nullable = false, length = 50)
    @Builder.Default
    private String category = Category.CONCEPT.name();

    /** 审核状态 */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = Status.PENDING.name();

    /** 图谱实体类型（审核通过后用于图谱写入） */
    @Column(name = "entity_type", length = 100)
    private String entityType;

    /** 原始对话上下文 */
    @Column(name = "source_context", columnDefinition = "TEXT")
    private String sourceContext;

    /** 审核人备注 */
    @Column(name = "reviewer_note", columnDefinition = "TEXT")
    private String reviewerNote;

    /** 是否已向量化 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean vectorized = false;

    /** 关联的图谱实体ID（审核通过后回写） */
    @Column(name = "graph_entity_id")
    private UUID graphEntityId;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}