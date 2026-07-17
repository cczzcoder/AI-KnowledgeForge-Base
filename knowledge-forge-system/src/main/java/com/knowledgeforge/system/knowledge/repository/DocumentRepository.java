package com.knowledgeforge.system.knowledge.repository;

import com.knowledgeforge.core.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Page<Document> findByKbIdAndDeletedFalse(UUID kbId, Pageable pageable);

    List<Document> findByKbId(UUID kbId);

    long countByKbIdAndDeletedFalse(UUID kbId);

    @Modifying
    @Query("DELETE FROM Document d WHERE d.kbId = :kbId")
    void deleteByKbId(@Param("kbId") UUID kbId);
}
