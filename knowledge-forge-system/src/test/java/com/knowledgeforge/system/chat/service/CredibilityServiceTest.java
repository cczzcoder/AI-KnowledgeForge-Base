package com.knowledgeforge.system.chat.service;

import com.knowledgeforge.core.entity.Document;
import com.knowledgeforge.system.chat.dto.CredibilityBreakdownDTO;
import com.knowledgeforge.system.config.CredibilityProperties;
import com.knowledgeforge.system.conversation.repository.ChatMessageRepository;
import com.knowledgeforge.system.knowledge.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CredibilityServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;

    private CredibilityService credibilityService;
    private CredibilityProperties credibilityProperties;

    @BeforeEach
    void setUp() {
        credibilityProperties = new CredibilityProperties();
        credibilityService = new CredibilityService(
                credibilityProperties,
                documentRepository,
                chatMessageRepository
        );
    }

    @Test
    void calculateCredibility_usesDefaultWeights() {
        UUID documentId = UUID.randomUUID();
        org.springframework.ai.document.Document retrieved = new org.springframework.ai.document.Document(
                "content",
                Map.of(
                        "similarity_score", 0.8,
                        "document_id", documentId.toString()
                )
        );

        when(documentRepository.findAllById(java.util.Set.of(documentId))).thenReturn(List.of(
                Document.builder().id(documentId).createdAt(LocalDateTime.now()).build()
        ));
        when(chatMessageRepository.countByFeedback("POSITIVE")).thenReturn(3L);
        when(chatMessageRepository.countByFeedback("NEGATIVE")).thenReturn(1L);

        CredibilityBreakdownDTO result = credibilityService.calculateCredibility(List.of(retrieved), UUID.randomUUID());

        assertThat(result.getSimilarityScore()).isEqualTo(0.8);
        assertThat(result.getDiversityScore()).isEqualTo(1.0);
        assertThat(result.getHistoryScore()).isEqualTo(0.75);
        assertThat(result.getOverallScore()).isEqualTo(0.87);
    }

    @Test
    void calculateCredibility_usesOverriddenWeights() {
        UUID documentId = UUID.randomUUID();
        credibilityProperties.setSimilarityWeight(0.1);
        credibilityProperties.setFreshnessWeight(0.2);
        credibilityProperties.setDiversityWeight(0.3);
        credibilityProperties.setHistoryWeight(0.4);

        org.springframework.ai.document.Document retrieved = new org.springframework.ai.document.Document(
                "content",
                Map.of(
                        "similarity_score", 0.8,
                        "document_id", documentId.toString()
                )
        );

        when(documentRepository.findAllById(java.util.Set.of(documentId))).thenReturn(List.of(
                Document.builder().id(documentId).createdAt(LocalDateTime.now()).build()
        ));
        when(chatMessageRepository.countByFeedback("POSITIVE")).thenReturn(3L);
        when(chatMessageRepository.countByFeedback("NEGATIVE")).thenReturn(1L);

        CredibilityBreakdownDTO result = credibilityService.calculateCredibility(List.of(retrieved), UUID.randomUUID());

        assertThat(result.getOverallScore()).isEqualTo(0.88);
    }

    @Test
    void calculateCredibility_usesOverriddenFreshnessDecayDays() {
        UUID documentId = UUID.randomUUID();
        credibilityProperties.setFreshnessDecayDays(10);

        org.springframework.ai.document.Document retrieved = new org.springframework.ai.document.Document(
                "content",
                Map.of(
                        "similarity_score", 0.8,
                        "document_id", documentId.toString()
                )
        );

        when(documentRepository.findAllById(java.util.Set.of(documentId))).thenReturn(List.of(
                Document.builder().id(documentId).createdAt(LocalDateTime.now().minusDays(30)).build()
        ));
        when(chatMessageRepository.countByFeedback("POSITIVE")).thenReturn(0L);
        when(chatMessageRepository.countByFeedback("NEGATIVE")).thenReturn(0L);

        CredibilityBreakdownDTO result = credibilityService.calculateCredibility(List.of(retrieved), UUID.randomUUID());

        assertThat(result.getFreshnessScore()).isLessThan(0.1);
    }
}
