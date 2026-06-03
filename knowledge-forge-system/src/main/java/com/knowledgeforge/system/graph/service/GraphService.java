package com.knowledgeforge.system.graph.service;

import com.knowledgeforge.core.entity.KnowledgeGraphEntity;
import com.knowledgeforge.core.entity.KnowledgeGraphRelation;
import com.knowledgeforge.system.graph.dto.EntityDetailDTO;
import com.knowledgeforge.system.graph.dto.EntityExtractionResult;
import com.knowledgeforge.system.graph.dto.GraphDTO;
import com.knowledgeforge.system.graph.dto.GraphEdgeDTO;
import com.knowledgeforge.system.graph.dto.GraphNodeDTO;
import com.knowledgeforge.system.graph.dto.RelatedEntityDTO;
import com.knowledgeforge.system.graph.repository.KnowledgeGraphEntityRepository;
import com.knowledgeforge.system.graph.repository.KnowledgeGraphRelationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GraphService {

    private final KnowledgeGraphEntityRepository entityRepository;
    private final KnowledgeGraphRelationRepository relationRepository;
    private final EntityExtractionService extractionService;

    private static final String GRAPH_CACHE = "graph";

    @Transactional
    @CacheEvict(value = GRAPH_CACHE, key = "#kbId")
    public void buildGraph(UUID kbId, UUID documentId, UUID chunkId, String text) {
        EntityExtractionResult extracted = extractionService.extractEntities(text);
        if (extracted.getEntities().isEmpty()) {
            return;
        }

        // 预加载已有实体并构建超集索引（O(1) 查找，替代 O(n) 线性扫描）
        List<KnowledgeGraphEntity> existingEntities = entityRepository.findByKbId(kbId);
        Map<String, UUID> supersetIndex = buildSupersetIndex(existingEntities);

        Map<String, UUID> entityNameToId = new HashMap<>();
        for (var entity : extracted.getEntities()) {
            UUID entityId = findOrCreateEntity(kbId, documentId, chunkId, entity, existingEntities, supersetIndex);
            entityNameToId.put(entity.getName(), entityId);
        }

        for (var relation : extracted.getRelations()) {
            UUID sourceId = entityNameToId.get(relation.getSourceEntity());
            UUID targetId = entityNameToId.get(relation.getTargetEntity());
            if (sourceId != null && targetId != null && !sourceId.equals(targetId)) {
                createRelationIfNotExists(kbId, sourceId, targetId, relation.getRelationType(),
                        relation.getDescription(), chunkId);
            }
        }

        log.info("知识图谱构建完成: kbId={}, docId={}, 新增 {} 个实体, {} 个关系",
                kbId, documentId, extracted.getEntities().size(), extracted.getRelations().size());
    }

    @Transactional
    @CacheEvict(value = GRAPH_CACHE, key = "#kbId")
    public void buildGraphFromChunks(UUID kbId, UUID documentId, List<String> chunks) {
        EntityExtractionResult extracted = extractionService.extractEntities(chunks);
        if (extracted.getEntities().isEmpty()) {
            return;
        }

        // 预加载已有实体并构建超集索引（O(1) 查找，替代 O(n) 线性扫描）
        List<KnowledgeGraphEntity> existingEntities = entityRepository.findByKbId(kbId);
        Map<String, UUID> supersetIndex = buildSupersetIndex(existingEntities);

        Map<String, UUID> entityNameToId = new HashMap<>();
        for (var entity : extracted.getEntities()) {
            UUID entityId = findOrCreateEntity(kbId, documentId, null, entity, existingEntities, supersetIndex);
            entityNameToId.put(entity.getName(), entityId);
        }

        for (var relation : extracted.getRelations()) {
            UUID sourceId = entityNameToId.get(relation.getSourceEntity());
            UUID targetId = entityNameToId.get(relation.getTargetEntity());
            if (sourceId != null && targetId != null && !sourceId.equals(targetId)) {
                createRelationIfNotExists(kbId, sourceId, targetId, relation.getRelationType(),
                        relation.getDescription(), null);
            }
        }

        log.info("批量知识图谱构建完成: kbId={}, docId={}, 新增 {} 个实体, {} 个关系",
                kbId, documentId, extracted.getEntities().size(), extracted.getRelations().size());
    }

    @Cacheable(value = GRAPH_CACHE, key = "#kbId", unless = "#result == null || #result.nodeCount == 0")
    public GraphDTO getGraph(UUID kbId) {
        List<KnowledgeGraphEntity> entities = entityRepository.findByKbId(kbId);
        List<KnowledgeGraphRelation> relations = relationRepository.findByKbId(kbId);

        Map<UUID, Integer> relationCounts = new HashMap<>();
        for (var rel : relations) {
            relationCounts.merge(rel.getSourceEntityId(), 1, Integer::sum);
            relationCounts.merge(rel.getTargetEntityId(), 1, Integer::sum);
        }

        List<GraphNodeDTO> nodes = entities.stream()
                .map(e -> GraphNodeDTO.builder()
                        .id(e.getId())
                        .name(e.getName())
                        .entityType(e.getEntityType())
                        .description(e.getDescription())
                        .sourceDocumentId(e.getSourceDocumentId())
                        .relationCount(relationCounts.getOrDefault(e.getId(), 0))
                        .build())
                .toList();

        List<GraphEdgeDTO> edges = relations.stream()
                .map(r -> GraphEdgeDTO.builder()
                        .id(r.getId())
                        .sourceId(r.getSourceEntityId())
                        .targetId(r.getTargetEntityId())
                        .relationType(r.getRelationType())
                        .description(r.getDescription())
                        .build())
                .toList();

        return GraphDTO.builder()
                .nodes(nodes)
                .edges(edges)
                .nodeCount(nodes.size())
                .edgeCount(edges.size())
                .build();
    }

    public List<String> expandQuery(UUID kbId, String query) {
        List<KnowledgeGraphEntity> matchedEntities = entityRepository.searchByName(kbId, query);
        if (matchedEntities.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> expandedTerms = new HashSet<>();
        for (var entity : matchedEntities) {
            expandedTerms.add(entity.getName());
            if (entity.getAliases() != null) {
                for (String alias : entity.getAliases().split(",")) {
                    expandedTerms.add(alias.trim());
                }
            }
            if (entity.getDescription() != null && !entity.getDescription().isBlank()) {
                expandedTerms.add(entity.getDescription());
            }
        }

        // 单次 JOIN 查询获取关联实体（替代原 relations + entities 两次查询）
        List<UUID> matchedEntityIds = matchedEntities.stream()
                .map(KnowledgeGraphEntity::getId)
                .collect(Collectors.toList());
        List<KnowledgeGraphEntity> relatedEntities = entityRepository.findRelatedEntities(kbId, matchedEntityIds);
        for (var entity : relatedEntities) {
            expandedTerms.add(entity.getName());
            if (entity.getDescription() != null) {
                expandedTerms.add(entity.getDescription());
            }
        }

        List<String> result = new ArrayList<>(expandedTerms);
        log.info("图谱查询扩展: query='{}' → {} 个扩展词", query, result.size());
        return result;
    }

    public List<UUID> findRelatedChunks(UUID kbId, String query) {
        List<KnowledgeGraphEntity> matchedEntities = entityRepository.searchByName(kbId, query);
        if (matchedEntities.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> matchedEntityIds = matchedEntities.stream()
                .map(KnowledgeGraphEntity::getId)
                .collect(Collectors.toList());

        Set<UUID> relatedEntityIds = new HashSet<>(matchedEntityIds);

        List<KnowledgeGraphRelation> relations = relationRepository.findRelationsByKbIdAndEntityIds(kbId, matchedEntityIds);
        for (var rel : relations) {
            relatedEntityIds.add(rel.getSourceEntityId());
            relatedEntityIds.add(rel.getTargetEntityId());
        }

        List<KnowledgeGraphEntity> allEntities = entityRepository.findAllById(relatedEntityIds);
        return allEntities.stream()
                .map(KnowledgeGraphEntity::getSourceChunkId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    @Transactional
    @CacheEvict(value = GRAPH_CACHE, key = "#kbId")
    public boolean addRelation(UUID kbId, String sourceEntityName, String targetEntityName,
                            String relationType, String description) {
        List<KnowledgeGraphEntity> sourceEntities = entityRepository.findByNameExact(kbId, sourceEntityName);
        List<KnowledgeGraphEntity> targetEntities = entityRepository.findByNameExact(kbId, targetEntityName);

        if (sourceEntities.isEmpty() || targetEntities.isEmpty()) {
            log.warn("添加关系失败: 实体不存在, source={}, target={}", sourceEntityName, targetEntityName);
            return false;
        }

        UUID sourceId = sourceEntities.get(0).getId();
        UUID targetId = targetEntities.get(0).getId();

        if (relationRepository.findExact(kbId, sourceId, targetId, relationType).isEmpty()) {
            KnowledgeGraphRelation relation = KnowledgeGraphRelation.builder()
                    .kbId(kbId)
                    .sourceEntityId(sourceId)
                    .targetEntityId(targetId)
                    .relationType(relationType)
                    .description(description)
                    .build();
            relationRepository.save(relation);
            log.info("手动添加关系: {} -[{}]-> {}", sourceEntityName, relationType, targetEntityName);
        }
        return true;
    }

    @Transactional
    @CacheEvict(value = GRAPH_CACHE, key = "#kbId")
    public void clearGraph(UUID kbId) {
        relationRepository.deleteByKbId(kbId);
        entityRepository.deleteByKbId(kbId);
        log.info("知识图谱已清空: kbId={}", kbId);
    }

    /**
     * 构建已有实体名的子片段倒排索引，用于 O(1) 超集查找。
     * 例如：已有"吴承恩" → 索引片段 "吴承"、"承恩" → "吴承恩"的ID。
     */
    private Map<String, UUID> buildSupersetIndex(List<KnowledgeGraphEntity> entities) {
        Map<String, UUID> index = new HashMap<>();
        for (var entity : entities) {
            String name = entity.getName();
            if (name == null || name.length() < 3) continue;
            // 对每个 >=2 字符的子串建立索引（取第一个匹配的实体）
            for (int i = 0; i <= name.length() - 2; i++) {
                for (int j = i + 2; j <= name.length(); j++) {
                    String fragment = name.substring(i, j);
                    index.putIfAbsent(fragment, entity.getId());
                }
            }
        }
        return index;
    }

    private UUID findOrCreateEntity(UUID kbId, UUID documentId, UUID chunkId,
                                     EntityExtractionResult.ExtractedEntity entity,
                                     List<KnowledgeGraphEntity> existingEntities,
                                     Map<String, UUID> supersetIndex) {
        // 精准匹配
        for (var existing : existingEntities) {
            if (existing.getName() != null && existing.getName().equals(entity.getName())) {
                return existing.getId();
            }
        }

        // 超集索引 O(1) 查找（替代 O(n) 线性扫描）
        UUID supersetId = supersetIndex.get(entity.getName());
        if (supersetId != null) {
            log.info("实体合并: '{}' 归并到已有超集实体", entity.getName());
            return supersetId;
        }

        String aliases = entity.getAliases() != null && !entity.getAliases().isEmpty()
                ? String.join(",", entity.getAliases())
                : null;

        KnowledgeGraphEntity newEntity = KnowledgeGraphEntity.builder()
                .kbId(kbId)
                .name(entity.getName())
                .entityType(entity.getEntityType() != null ? entity.getEntityType() : "概念")
                .description(entity.getDescription())
                .aliases(aliases)
                .sourceDocumentId(documentId)
                .sourceChunkId(chunkId)
                .build();
        UUID savedId = entityRepository.save(newEntity).getId();
        // 将新实体加入索引，供后续实体查找
        existingEntities.add(newEntity);
        addToSupersetIndex(supersetIndex, newEntity);
        return savedId;
    }

    /**
     * 将新实体及其子片段加入超集索引。
     */
    private void addToSupersetIndex(Map<String, UUID> index, KnowledgeGraphEntity entity) {
        String name = entity.getName();
        if (name == null || name.length() < 3) return;
        for (int i = 0; i <= name.length() - 2; i++) {
            for (int j = i + 2; j <= name.length(); j++) {
                index.putIfAbsent(name.substring(i, j), entity.getId());
            }
        }
    }

    private void createRelationIfNotExists(UUID kbId, UUID sourceId, UUID targetId,
                                            String relationType, String description, UUID chunkId) {
        if (relationRepository.findExact(kbId, sourceId, targetId,
                relationType != null ? relationType : "关联").isEmpty()) {
            KnowledgeGraphRelation relation = KnowledgeGraphRelation.builder()
                    .kbId(kbId)
                    .sourceEntityId(sourceId)
                    .targetEntityId(targetId)
                    .relationType(relationType != null ? relationType : "关联")
                    .description(description)
                    .sourceChunkId(chunkId)
                    .build();
            relationRepository.save(relation);
        }
    }

    public Page<GraphNodeDTO> getEntities(UUID kbId, int page, int size) {
        Page<KnowledgeGraphEntity> entityPage = entityRepository.findByKbId(kbId, PageRequest.of(page, size));

        // 使用子查询计算 entity 级别的 relationCount，避免加载全表关系
        List<UUID> entityIds = entityPage.getContent().stream()
                .map(KnowledgeGraphEntity::getId).toList();
        List<Object[]> countRows = entityIds.isEmpty() ? List.of()
                : entityRepository.findEntitiesWithRelationCountByIds(entityIds);
        Map<UUID, Long> relationCounts = new HashMap<>();
        for (var row : countRows) {
            relationCounts.put((UUID) row[0], ((Number) row[1]).longValue());
        }

        return entityPage.map(e -> GraphNodeDTO.builder()
                .id(e.getId())
                .name(e.getName())
                .entityType(e.getEntityType())
                .description(e.getDescription())
                .sourceDocumentId(e.getSourceDocumentId())
                .relationCount(relationCounts.getOrDefault(e.getId(), 0L).intValue())
                .build());
    }

    public EntityDetailDTO getEntityDetail(UUID kbId, String entityName) {
        List<KnowledgeGraphEntity> entities = entityRepository.findByNameExact(kbId, entityName);
        if (entities.isEmpty()) {
            return null;
        }

        KnowledgeGraphEntity entity = entities.get(0);
        List<KnowledgeGraphRelation> relations = relationRepository.findRelationsByKbIdAndEntityId(kbId, entity.getId());

        Set<UUID> relatedEntityIds = new HashSet<>();
        for (var rel : relations) {
            if (rel.getSourceEntityId().equals(entity.getId())) {
                relatedEntityIds.add(rel.getTargetEntityId());
            } else {
                relatedEntityIds.add(rel.getSourceEntityId());
            }
        }

        Map<UUID, KnowledgeGraphEntity> relatedEntityMap = new HashMap<>();
        if (!relatedEntityIds.isEmpty()) {
            List<KnowledgeGraphEntity> relatedEntities = entityRepository.findAllById(relatedEntityIds);
            for (var re : relatedEntities) {
                relatedEntityMap.put(re.getId(), re);
            }
        }

        List<RelatedEntityDTO> relatedDTOs = new ArrayList<>();
        List<GraphEdgeDTO> edgeDTOs = new ArrayList<>();
        for (var rel : relations) {
            boolean isSource = rel.getSourceEntityId().equals(entity.getId());
            UUID relatedId = isSource ? rel.getTargetEntityId() : rel.getSourceEntityId();
            KnowledgeGraphEntity relatedEntity = relatedEntityMap.get(relatedId);
            if (relatedEntity != null) {
                relatedDTOs.add(RelatedEntityDTO.builder()
                        .id(relatedEntity.getId())
                        .name(relatedEntity.getName())
                        .entityType(relatedEntity.getEntityType())
                        .relationType(rel.getRelationType())
                        .relationDescription(rel.getDescription())
                        .build());
            }
            edgeDTOs.add(GraphEdgeDTO.builder()
                    .id(rel.getId())
                    .sourceId(rel.getSourceEntityId())
                    .targetId(rel.getTargetEntityId())
                    .relationType(rel.getRelationType())
                    .description(rel.getDescription())
                    .build());
        }

        return EntityDetailDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .entityType(entity.getEntityType())
                .description(entity.getDescription())
                .aliases(entity.getAliases())
                .sourceDocumentId(entity.getSourceDocumentId())
                .sourceChunkId(entity.getSourceChunkId())
                .relatedEntities(relatedDTOs)
                .relatedEdges(edgeDTOs)
                .relationCount(relations.size())
                .build();
    }

    public GraphDTO getSubgraph(UUID kbId, String entityName) {
        List<KnowledgeGraphEntity> entities = entityRepository.findByNameExact(kbId, entityName);
        if (entities.isEmpty()) {
            return GraphDTO.builder().nodes(List.of()).edges(List.of()).nodeCount(0).edgeCount(0).build();
        }

        UUID centerId = entities.get(0).getId();
        List<KnowledgeGraphRelation> relations = relationRepository.findRelationsByKbIdAndEntityId(kbId, centerId);

        Set<UUID> nodeIds = new HashSet<>();
        nodeIds.add(centerId);
        for (var rel : relations) {
            nodeIds.add(rel.getSourceEntityId());
            nodeIds.add(rel.getTargetEntityId());
        }

        List<KnowledgeGraphEntity> allEntities = entityRepository.findAllById(nodeIds);

        Map<UUID, Integer> relationCounts = new HashMap<>();
        for (var rel : relations) {
            relationCounts.merge(rel.getSourceEntityId(), 1, Integer::sum);
            relationCounts.merge(rel.getTargetEntityId(), 1, Integer::sum);
        }

        List<GraphNodeDTO> nodes = allEntities.stream()
                .map(e -> GraphNodeDTO.builder()
                        .id(e.getId())
                        .name(e.getName())
                        .entityType(e.getEntityType())
                        .description(e.getDescription())
                        .sourceDocumentId(e.getSourceDocumentId())
                        .relationCount(relationCounts.getOrDefault(e.getId(), 0))
                        .build())
                .toList();

        List<GraphEdgeDTO> edges = relations.stream()
                .map(r -> GraphEdgeDTO.builder()
                        .id(r.getId())
                        .sourceId(r.getSourceEntityId())
                        .targetId(r.getTargetEntityId())
                        .relationType(r.getRelationType())
                        .description(r.getDescription())
                        .build())
                .toList();

        return GraphDTO.builder()
                .nodes(nodes)
                .edges(edges)
                .nodeCount(nodes.size())
                .edgeCount(edges.size())
                .build();
    }

    public List<GraphNodeDTO> searchGraph(UUID kbId, String keyword) {
        List<KnowledgeGraphEntity> entities = entityRepository.searchByName(kbId, keyword);
        List<KnowledgeGraphRelation> relations = relationRepository.findByKbId(kbId);

        Map<UUID, Integer> relationCounts = new HashMap<>();
        for (var rel : relations) {
            relationCounts.merge(rel.getSourceEntityId(), 1, Integer::sum);
            relationCounts.merge(rel.getTargetEntityId(), 1, Integer::sum);
        }

        return entities.stream()
                .map(e -> GraphNodeDTO.builder()
                        .id(e.getId())
                        .name(e.getName())
                        .entityType(e.getEntityType())
                        .description(e.getDescription())
                        .sourceDocumentId(e.getSourceDocumentId())
                        .relationCount(relationCounts.getOrDefault(e.getId(), 0))
                        .build())
                .toList();
    }
}