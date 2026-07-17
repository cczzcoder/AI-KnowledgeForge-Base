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

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "conversation", indexes = {
        @Index(name = "idx_conversation_deleted", columnList = "deleted"),
        @Index(name = "idx_conversation_kb_id", columnList = "kb_id"),
        @Index(name = "idx_conversation_cleanup_status", columnList = "cleanup_status")
})
public class Conversation {

    public enum CleanupStatus {
        ACTIVE,
        EXPIRING,
        EXPIRED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String title;

    /** 关联的知识库ID（可选，在知识库上下文中对话时设置） */
    @Column(name = "kb_id")
    private UUID kbId;

    @Builder.Default
    @Column(nullable = false)
    private Boolean deleted = false;

    @Builder.Default
    @Column(name = "cleanup_status", nullable = false, length = 20)
    private String cleanupStatus = CleanupStatus.ACTIVE.name();

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "remind_at")
    private LocalDateTime remindAt;

    @Column(name = "reminded_at")
    private LocalDateTime remindedAt;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}