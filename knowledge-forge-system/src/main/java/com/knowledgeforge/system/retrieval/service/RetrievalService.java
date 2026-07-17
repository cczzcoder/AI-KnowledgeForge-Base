package com.knowledgeforge.system.retrieval.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgeforge.core.shared.constant.SystemConstants;
import com.knowledgeforge.system.config.VectorRetrievalProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalService {

    private final PgVectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    private final JdbcTemplate jdbcTemplate;
    private final VectorRetrievalProperties vectorRetrievalProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Document> retrieve(String query, UUID kbId) {
        String safeKbId = kbId.toString();
        if (!safeKbId.matches("^[a-zA-Z0-9\\-]+$")) {
            throw new IllegalArgumentException("非法的知识库ID: " + safeKbId);
        }

        try {
            float[] queryEmbedding = embeddingModel.embed(query);
            log.info("Embedding生成成功, 维度: {}, 前3个值: [{}, {}, {}]",
                    queryEmbedding.length,
                    queryEmbedding.length > 0 ? queryEmbedding[0] : "N/A",
                    queryEmbedding.length > 1 ? queryEmbedding[1] : "N/A",
                    queryEmbedding.length > 2 ? queryEmbedding[2] : "N/A");

            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < queryEmbedding.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(queryEmbedding[i]);
            }
            sb.append("]");
            String vectorStr = sb.toString();
            log.info("向量字符串长度: {}, 前100字符: {}", vectorStr.length(),
                    vectorStr.length() > 100 ? vectorStr.substring(0, 100) : vectorStr);

            String sql = "SELECT vs.content, vs.metadata::text as metadata_text, 1 - (vs.embedding <=> ?::vector) AS similarity "
                    + "FROM vector_store vs "
                    + "WHERE vs.metadata->>'kb_id' = ? "
                    + "AND 1 - (vs.embedding <=> ?::vector) >= ? "
                    + "ORDER BY vs.embedding <=> ?::vector "
                    + "LIMIT ?";

            log.info("执行JDBC查询, kbId: {}, maxResults: {}, similarityThreshold: {}",
                    safeKbId,
                    SystemConstants.RETRIEVAL_MAX_RESULTS,
                    vectorRetrievalProperties.getSimilarityThreshold());

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    sql,
                    vectorStr,
                    safeKbId,
                    vectorStr,
                    vectorRetrievalProperties.getSimilarityThreshold(),
                    vectorStr,
                    SystemConstants.RETRIEVAL_MAX_RESULTS);

            log.info("JDBC向量检索: 召回 {} 条", rows.size());
            if (!rows.isEmpty()) {
                List<Double> topSimilarities = rows.stream()
                        .map(row -> row.get("similarity"))
                        .filter(Number.class::isInstance)
                        .map(Number.class::cast)
                        .map(Number::doubleValue)
                        .limit(5)
                        .toList();
                Map<String, Object> firstRow = rows.get(0);
                log.info("向量检索top similarities: {}", topSimilarities);
                log.info("第一条结果: similarity={}, content前50字={}",
                        firstRow.get("similarity"),
                        firstRow.get("content") != null
                                ? firstRow.get("content").toString().substring(0,
                                        Math.min(50, firstRow.get("content").toString().length()))
                                : "null");
            } else {
                log.info("向量检索未命中任何结果, kbId={}, similarityThreshold={}",
                        safeKbId,
                        vectorRetrievalProperties.getSimilarityThreshold());
            }

            List<Document> results = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String content = (String) row.get("content");
                String metadataText = (String) row.get("metadata_text");
                Map<String, Object> metadata = parseMetadataJson(metadataText);
                Document doc = new Document(content, (Map) metadata);
                if (row.get("similarity") != null) {
                    doc.getMetadata().put("vector_similarity", ((Number) row.get("similarity")).doubleValue());
                }
                results.add(doc);
            }

            return results.stream()
                    .sorted(Comparator.comparingDouble(
                            (Document d) -> ((Number) d.getMetadata().getOrDefault("vector_similarity", 0.0)).doubleValue()
                    ).reversed())
                    .limit(SystemConstants.RETRIEVAL_TOP_K)
                    .toList();
        } catch (Exception e) {
            log.error("JDBC检索失败: {} - {}", e.getClass().getName(), e.getMessage(), e);
            return List.of();
        }
    }

    private Map<String, Object> parseMetadataJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("解析metadata JSON失败: {}", json, e);
            return Map.of();
        }
    }

    public List<Document> retrieveMultipleKbs(String query, List<UUID> kbIds) {
        return kbIds.stream()
                .flatMap(kbId -> retrieve(query, kbId).stream())
                .sorted(Comparator.comparingDouble(
                        (Document d) -> ((Number) d.getMetadata().getOrDefault("vector_similarity", 0.0)).doubleValue()
                ).reversed())
                .limit(SystemConstants.RETRIEVAL_TOP_K)
                .toList();
    }
}