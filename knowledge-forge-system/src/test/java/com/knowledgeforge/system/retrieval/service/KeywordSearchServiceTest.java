package com.knowledgeforge.system.retrieval.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgeforge.system.config.KeywordRetrievalProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeywordSearchServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private KeywordSearchService keywordSearchService;
    private KeywordRetrievalProperties keywordRetrievalProperties;

    @BeforeEach
    void setUp() {
        keywordRetrievalProperties = new KeywordRetrievalProperties();
        keywordSearchService = new KeywordSearchService(jdbcTemplate, keywordRetrievalProperties);
    }

    @Test
    void search_usesDefaultFallbackScoreWhenFullTextReturnsNoRows() {
        UUID kbId = UUID.randomUUID();
        when(jdbcTemplate.queryForList(anyString(), eq("query"), eq(kbId.toString()), eq("query"), eq(10)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(anyString(), eq(kbId.toString()), eq("%query%"), eq(5)))
                .thenReturn(List.of(Map.of(
                        "content", "fallback-content",
                        "metadata_text", new ObjectMapper().valueToTree(Map.of("chunk_id", UUID.randomUUID().toString())).toString()
                )));

        List<Document> results = keywordSearchService.search("query", kbId, 5);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getMetadata()).containsEntry("keyword_score", 0.1);
    }

    @Test
    void search_usesOverriddenFallbackScoreWhenConfigured() {
        UUID kbId = UUID.randomUUID();
        keywordRetrievalProperties.setFallbackScore(0.25);
        when(jdbcTemplate.queryForList(anyString(), eq("query"), eq(kbId.toString()), eq("query"), eq(10)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(anyString(), eq(kbId.toString()), eq("%query%"), eq(5)))
                .thenReturn(List.of(Map.of(
                        "content", "fallback-content",
                        "metadata_text", new ObjectMapper().valueToTree(Map.of("chunk_id", UUID.randomUUID().toString())).toString()
                )));

        List<Document> results = keywordSearchService.search("query", kbId, 5);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getMetadata()).containsEntry("keyword_score", 0.25);
    }

    @Test
    void search_keepsDatabaseScoreWhenFullTextSearchHits() {
        UUID kbId = UUID.randomUUID();
        keywordRetrievalProperties.setFallbackScore(0.25);
        when(jdbcTemplate.queryForList(anyString(), eq("query"), eq(kbId.toString()), eq("query"), eq(10)))
                .thenReturn(List.of(Map.of(
                        "content", "fts-content",
                        "metadata_text", new ObjectMapper().valueToTree(Map.of("chunk_id", UUID.randomUUID().toString())).toString(),
                        "score", 0.73
                )));

        List<Document> results = keywordSearchService.search("query", kbId, 5);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getMetadata()).containsEntry("keyword_score", 0.73);
    }
}
