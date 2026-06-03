package com.knowledgeforge.system.graph.repository;

import com.knowledgeforge.core.entity.KnowledgeGraphEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface KnowledgeGraphEntityRepository extends JpaRepository<KnowledgeGraphEntity, UUID> {

    List<KnowledgeGraphEntity> findByKbId(UUID kbId);

    Page<KnowledgeGraphEntity> findByKbId(UUID kbId, Pageable pageable);

    @Query("SELECT e FROM KnowledgeGraphEntity e WHERE e.kbId = :kbId AND LOWER(e.name) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<KnowledgeGraphEntity> searchByName(@Param("kbId") UUID kbId, @Param("keyword") String keyword);

    @Query("SELECT e FROM KnowledgeGraphEntity e WHERE e.kbId = :kbId AND e.entityType = :type")
    List<KnowledgeGraphEntity> findByKbIdAndType(@Param("kbId") UUID kbId, @Param("type") String type);

    @Query("SELECT e FROM KnowledgeGraphEntity e WHERE e.kbId = :kbId AND LOWER(e.name) = LOWER(:name)")
    List<KnowledgeGraphEntity> findByNameExact(@Param("kbId") UUID kbId, @Param("name") String name);

    /** 通过 JOIN 查询直接获取与指定实体关联的其他实体，减少数据库往返 */
    @Query("SELECT DISTINCT re FROM KnowledgeGraphRelation r " +
           "JOIN KnowledgeGraphEntity e ON (e.id = r.sourceEntityId OR e.id = r.targetEntityId) " +
           "JOIN KnowledgeGraphEntity re ON (re.id = " +
           "   CASE WHEN r.sourceEntityId = e.id THEN r.targetEntityId ELSE r.sourceEntityId END) " +
           "WHERE r.kbId = :kbId AND e.id IN :entityIds")
    List<KnowledgeGraphEntity> findRelatedEntities(@Param("kbId") UUID kbId, @Param("entityIds") List<UUID> entityIds);

    /** 使用子查询计算实体关系数，避免加载全表关系 */
    @Query("SELECT e, (SELECT COUNT(r) FROM KnowledgeGraphRelation r " +
           "WHERE r.sourceEntityId = e.id OR r.targetEntityId = e.id) " +
           "FROM KnowledgeGraphEntity e WHERE e.kbId = :kbId")
    List<Object[]> findEntitiesWithRelationCount(@Param("kbId") UUID kbId);

    /** 按指定实体ID列表计算关系数（分页场景） */
    @Query("SELECT e.id, (SELECT COUNT(r) FROM KnowledgeGraphRelation r " +
           "WHERE r.sourceEntityId = e.id OR r.targetEntityId = e.id) " +
           "FROM KnowledgeGraphEntity e WHERE e.id IN :entityIds")
    List<Object[]> findEntitiesWithRelationCountByIds(@Param("entityIds") List<UUID> entityIds);

    int countByKbId(UUID kbId);

    void deleteByKbId(UUID kbId);
}