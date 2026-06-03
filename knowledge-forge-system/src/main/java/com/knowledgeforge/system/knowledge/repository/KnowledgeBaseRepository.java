package com.knowledgeforge.system.knowledge.repository;

import com.knowledgeforge.core.entity.KnowledgeBase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, UUID> {

    Page<KnowledgeBase> findByDeletedFalse(Pageable pageable);

    List<KnowledgeBase> findByDeletedFalse();
}