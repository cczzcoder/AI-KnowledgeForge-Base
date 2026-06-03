package com.knowledgeforge.system.graph.repository;

import com.knowledgeforge.core.entity.KnowledgeGraphRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KnowledgeGraphRelationRepository extends JpaRepository<KnowledgeGraphRelation, UUID> {

    List<KnowledgeGraphRelation> findByKbId(UUID kbId);

    @Query("SELECT r FROM KnowledgeGraphRelation r WHERE r.sourceEntityId = :entityId OR r.targetEntityId = :entityId")
    List<KnowledgeGraphRelation> findRelationsByEntityId(@Param("entityId") UUID entityId);

    @Query("SELECT r FROM KnowledgeGraphRelation r WHERE r.kbId = :kbId AND (r.sourceEntityId = :entityId OR r.targetEntityId = :entityId)")
    List<KnowledgeGraphRelation> findRelationsByKbIdAndEntityId(@Param("kbId") UUID kbId, @Param("entityId") UUID entityId);

    @Query("SELECT r FROM KnowledgeGraphRelation r WHERE r.kbId = :kbId AND (r.sourceEntityId IN :entityIds OR r.targetEntityId IN :entityIds)")
    List<KnowledgeGraphRelation> findRelationsByKbIdAndEntityIds(@Param("kbId") UUID kbId, @Param("entityIds") List<UUID> entityIds);

    @Query("SELECT r FROM KnowledgeGraphRelation r WHERE r.kbId = :kbId AND r.sourceEntityId = :sourceId AND r.targetEntityId = :targetId AND r.relationType = :relationType")
    Optional<KnowledgeGraphRelation> findExact(@Param("kbId") UUID kbId, @Param("sourceId") UUID sourceId, @Param("targetId") UUID targetId, @Param("relationType") String relationType);

    void deleteByKbId(UUID kbId);

    int countByKbId(UUID kbId);
}