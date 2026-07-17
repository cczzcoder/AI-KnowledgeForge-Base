package com.knowledgeforge.system.knowledge.repository;

import com.knowledgeforge.core.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(UUID documentId);

    List<DocumentChunk> findByDocumentIdAndChunkIndexBetweenOrderByChunkIndexAsc(UUID documentId,
                                                                                 Integer startChunkIndex,
                                                                                 Integer endChunkIndex);

    List<DocumentChunk> findByParentChunkIdIn(List<UUID> parentChunkIds);

    @Modifying
    @Transactional
    @Query("DELETE FROM DocumentChunk c WHERE c.documentId = :documentId")
    void deleteByDocumentId(@Param("documentId") UUID documentId);

    @Modifying
    @Query("UPDATE DocumentChunk c SET c.status = :status, c.errorMessage = :errorMessage WHERE c.id = :id")
    void updateStatus(@Param("id") UUID id, @Param("status") String status,
                      @Param("errorMessage") String errorMessage);

    List<DocumentChunk> findByStatus(String status);
}