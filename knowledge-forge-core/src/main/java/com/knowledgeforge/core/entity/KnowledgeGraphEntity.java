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
@Table(name = "knowledge_graph_entity", indexes = {
        @Index(name = "idx_kge_kb_id", columnList = "kb_id"),
        @Index(name = "idx_kge_name", columnList = "name"),
        @Index(name = "idx_kge_type", columnList = "entity_type")
})
public class KnowledgeGraphEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "kb_id", nullable = false)
    private UUID kbId;

    @Column(nullable = false, length = 500)
    private String name;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "source_chunk_id")
    private UUID sourceChunkId;

    @Column(name = "source_document_id")
    private UUID sourceDocumentId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "aliases", columnDefinition = "TEXT")
    private String aliases;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}