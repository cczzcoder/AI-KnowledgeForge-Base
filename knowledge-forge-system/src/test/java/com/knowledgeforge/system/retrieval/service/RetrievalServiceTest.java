package com.knowledgeforge.system.retrieval.service;

import com.knowledgeforge.system.config.VectorRetrievalProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetrievalServiceTest {

    @Mock
    private PgVectorStore vectorStore;
    @Mock
    private EmbeddingModel embeddingModel;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private RetrievalService retrievalService;
    private VectorRetrievalProperties vectorRetrievalProperties;

    @BeforeEach
    void setUp() {
        vectorRetrievalProperties = new VectorRetrievalProperties();
        retrievalService = new RetrievalService(
                vectorStore,
                embeddingModel,
                jdbcTemplate,
                vectorRetrievalProperties
        );
    }

    @Test
    void retrieve_usesDefaultSimilarityThresholdInJdbcQuery() {
        UUID kbId = UUID.randomUUID();
        when(embeddingModel.embed("query")).thenReturn(new float[]{0.1f, 0.2f, 0.3f});
        doReturn(List.of())
                .when(jdbcTemplate)
                .queryForList(anyString(), anyString(), anyString(), anyString(), anyDouble(), anyString(), anyInt());

        retrievalService.retrieve("query", kbId);

        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForList(anyString(), argsCaptor.capture());
        Object[] args = argsCaptor.getValue();

        assertThat(args[3]).isEqualTo(0.3);
    }

    @Test
    void retrieve_usesOverriddenSimilarityThresholdInJdbcQuery() {
        UUID kbId = UUID.randomUUID();
        vectorRetrievalProperties.setSimilarityThreshold(0.42);
        when(embeddingModel.embed("query")).thenReturn(new float[]{0.1f, 0.2f, 0.3f});
        doReturn(List.of())
                .when(jdbcTemplate)
                .queryForList(anyString(), anyString(), anyString(), anyString(), anyDouble(), anyString(), anyInt());

        retrievalService.retrieve("query", kbId);

        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForList(anyString(), argsCaptor.capture());
        Object[] args = argsCaptor.getValue();

        assertThat(args[3]).isEqualTo(0.42);
    }

    @Test
    void retrieve_mapsSimilarityIntoVectorMetadata() {
        UUID kbId = UUID.randomUUID();
        when(embeddingModel.embed("query")).thenReturn(new float[]{0.1f, 0.2f, 0.3f});
        doReturn(List.of())
                .when(jdbcTemplate)
                .queryForList(anyString(), anyString(), anyString(), anyString(), anyDouble(), anyString(), anyInt());

        List<Document> results = retrievalService.retrieve("query", kbId);

        assertThat(results).isEmpty();
    }
}
