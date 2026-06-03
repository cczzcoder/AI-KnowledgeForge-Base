package com.knowledgeforge.system.knowledge.repository;

import com.knowledgeforge.core.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Page<Document> findByKbIdAndDeletedFalse(UUID kbId, Pageable pageable);

    long countByKbIdAndDeletedFalse(UUID kbId);
}