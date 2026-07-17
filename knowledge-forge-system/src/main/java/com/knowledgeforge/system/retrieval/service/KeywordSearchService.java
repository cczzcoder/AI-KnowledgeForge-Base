package com.knowledgeforge.system.retrieval.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgeforge.system.config.KeywordRetrievalProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeywordSearchService {

    private final JdbcTemplate jdbcTemplate;
    private final KeywordRetrievalProperties keywordRetrievalProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Document> search(String query, UUID kbId, int topK) {
        String safeKbId = kbId.toString();
        if (!safeKbId.matches("^[a-zA-Z0-9\\-]+$")) {
            throw new IllegalArgumentException("非法的知识库ID: " + safeKbId);
        }

        try {
            String sql = "SELECT vs.content, vs.metadata::text as metadata_text, "
                    + "ts_rank(to_tsvector('simple', vs.content), plainto_tsquery('simple', ?)) AS score "
                    + "FROM vector_store vs "
                    + "WHERE vs.metadata->>'kb_id' = ? "
                    + "AND to_tsvector('simple', vs.content) @@ plainto_tsquery('simple', ?) "
                    + "ORDER BY score DESC "
                    + "LIMIT ?";

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    sql, query, safeKbId, query, topK * 2);

            log.info("关键词检索: 召回 {} 条", rows.size());

            List<Document> results = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String content = (String) row.get("content");
                String metadataText = (String) row.get("metadata_text");
                Map<String, Object> metadata = parseMetadataJson(metadataText);
                Document doc = new Document(content, (Map) metadata);
                if (row.get("score") != null) {
                    doc.getMetadata().put("keyword_score", ((Number) row.get("score")).doubleValue());
                }
                results.add(doc);
            }

            if (results.isEmpty()) {
                results = fallbackSearch(query, safeKbId, topK);
            }

            return results;
        } catch (Exception e) {
            log.error("关键词检索失败: {}", e.getMessage(), e);
            return fallbackSearch(query, safeKbId, topK);
        }
    }

    private List<Document> fallbackSearch(String query, String kbId, int topK) {
        try {
            String sql = "SELECT vs.content, vs.metadata::text as metadata_text "
                    + "FROM vector_store vs "
                    + "WHERE vs.metadata->>'kb_id' = ? "
                    + "AND vs.content ILIKE ? "
                    + "LIMIT ?";

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    sql, kbId, "%" + query + "%", topK);

            log.info("降级关键词检索(ILIKE): 召回 {} 条", rows.size());

            List<Document> results = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String content = (String) row.get("content");
                String metadataText = (String) row.get("metadata_text");
                Map<String, Object> metadata = parseMetadataJson(metadataText);
                Document doc = new Document(content, (Map) metadata);
                doc.getMetadata().put("keyword_score", keywordRetrievalProperties.getFallbackScore());
                results.add(doc);
            }
            return results;
        } catch (Exception e) {
            log.warn("降级关键词检索也失败: {}", e.getMessage());
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
            return Map.of();
        }
    }
}