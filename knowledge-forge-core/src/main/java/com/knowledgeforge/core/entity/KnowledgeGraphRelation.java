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
@Table(name = "knowledge_graph_relation", indexes = {
        @Index(name = "idx_kgr_kb_id", columnList = "kb_id"),
        @Index(name = "idx_kgr_source", columnList = "source_entity_id"),
        @Index(name = "idx_kgr_target", columnList = "target_entity_id")
})
public class KnowledgeGraphRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "kb_id", nullable = false)
    private UUID kbId;

    @Column(name = "source_entity_id", nullable = false)
    private UUID sourceEntityId;

    @Column(name = "target_entity_id", nullable = false)
    private UUID targetEntityId;

    @Column(name = "relation_type", nullable = false, length = 100)
    private String relationType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "source_chunk_id")
    private UUID sourceChunkId;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}