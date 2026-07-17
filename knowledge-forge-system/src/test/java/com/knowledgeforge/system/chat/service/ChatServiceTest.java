package com.knowledgeforge.system.chat.service;

import com.knowledgeforge.core.entity.ChatMessage;
import com.knowledgeforge.core.entity.Conversation;
import com.knowledgeforge.system.chat.dto.ChatRequest;
import com.knowledgeforge.system.chat.dto.ChatResponse;
import com.knowledgeforge.system.chat.dto.CredibilityBreakdownDTO;
import com.knowledgeforge.system.chat.observability.RagMetricsAggregator;
import com.knowledgeforge.system.config.ChatContextProperties;
import com.knowledgeforge.system.config.ChatExtractionProperties;
import com.knowledgeforge.system.conversation.repository.ChatMessageRepository;
import com.knowledgeforge.system.conversation.repository.ConversationRepository;
import com.knowledgeforge.system.conversation.service.ConversationService;
import com.knowledgeforge.system.knowledge.repository.DocumentRepository;
import com.knowledgeforge.system.knowledgecard.service.KnowledgeExtractionService;
import com.knowledgeforge.system.retrieval.service.HybridSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatClient chatClient;
    @Mock
    private HybridSearchService hybridSearchService;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private CredibilityService credibilityService;
    @Mock
    private KnowledgeExtractionService knowledgeExtractionService;
    @Mock
    private ConversationRepository conversationRepository;

    private ChatService chatService;
    private ChatContextProperties chatContextProperties;
    private ChatExtractionProperties chatExtractionProperties;
    private ConversationService conversationService;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callSpec;
    private RagMetricsAggregator ragMetricsAggregator;

    @BeforeEach
    void setUp() {
        conversationService = new ConversationService(
                conversationRepository,
                chatMessageRepository,
                knowledgeExtractionService
        );
        ragMetricsAggregator = new RagMetricsAggregator(50, 50);
        chatContextProperties = new ChatContextProperties();
        chatExtractionProperties = new ChatExtractionProperties();
        chatService = new ChatService(
                chatContextProperties,
                chatExtractionProperties,
                chatClient,
                hybridSearchService,
                documentRepository,
                chatMessageRepository,
                credibilityService,
                knowledgeExtractionService,
                conversationService,
                ragMetricsAggregator
        );
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callSpec = mock(ChatClient.CallResponseSpec.class);
    }

    @Test
    void chat_mapsRetrievalMetadataIntoSources_forAnchorAndAdjacentChunks() {
        UUID kbId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID anchorChunkId = UUID.randomUUID();
        UUID adjacentChunkId = UUID.randomUUID();
        LocalDateTime updatedAt = LocalDateTime.now().minusDays(2);

        ChatRequest request = new ChatRequest();
        request.setMessage("什么是配置项？");
        request.setKbId(kbId);
        request.setConversationId(conversationId);

        Document anchor = new Document(longText("anchor"), Map.ofEntries(
                Map.entry("chunk_id", anchorChunkId.toString()),
                Map.entry("document_id", documentId.toString()),
                Map.entry("section_title", "配置"),
                Map.entry("section_path", "手册/配置"),
                Map.entry("chunk_index", 1),
                Map.entry("hybrid_score", 0.91234),
                Map.entry("retrieval_role", "anchor"),
                Map.entry("anchor_chunk_id", anchorChunkId.toString()),
                Map.entry("evidence_hits", 2),
                Map.entry("matched_sources", java.util.Set.of("vector", "keyword"))
        ));
        Document adjacent = new Document("adjacent-content", Map.ofEntries(
                Map.entry("chunk_id", adjacentChunkId.toString()),
                Map.entry("document_id", documentId.toString()),
                Map.entry("section_title", "配置"),
                Map.entry("section_path", "手册/配置"),
                Map.entry("chunk_index", 2),
                Map.entry("vector_similarity", 0.75),
                Map.entry("adjacent", true),
                Map.entry("retrieval_role", "adjacent"),
                Map.entry("anchor_chunk_id", anchorChunkId.toString()),
                Map.entry("expansion_offset", 1),
                Map.entry("same_section", true),
                Map.entry("evidence_hits", 2),
                Map.entry("matched_sources", java.util.Set.of("vector", "keyword"))
        ));

        Conversation persistedConversation = mockConversationPersistence(conversationId, updatedAt);
        when(hybridSearchService.hybridSearch(eq("什么是配置项？"), eq(kbId), any())).thenReturn(List.of(anchor, adjacent));
        when(documentRepository.findAllById(any())).thenReturn(List.of(
                com.knowledgeforge.core.entity.Document.builder().id(documentId).title("配置手册").build()
        ));
        when(credibilityService.calculateCredibility(any(), eq(kbId))).thenReturn(CredibilityBreakdownDTO.builder().overallScore(0.9).build());
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.chatResponse()).thenReturn(mockAiChatResponse("这是回答"));

        ChatResponse response = chatService.chat(request);

        assertThat(response.getSources()).hasSize(2);
        assertThat(response.getSources().get(0).getRetrievalRole()).isEqualTo("anchor");
        assertThat(response.getSources().get(0).getAdjacent()).isFalse();
        assertThat(response.getSources().get(0).getSimilarityScore()).isEqualTo(0.9123);
        assertThat(response.getSources().get(0).getEvidenceHits()).isEqualTo(2);
        assertThat(response.getSources().get(0).getMatchedSources()).containsExactlyInAnyOrder("vector", "keyword");
        assertThat(response.getSources().get(0).getSnippet()).endsWith("...");

        assertThat(response.getSources().get(1).getRetrievalRole()).isEqualTo("adjacent");
        assertThat(response.getSources().get(1).getAdjacent()).isTrue();
        assertThat(response.getSources().get(1).getAnchorChunkId()).isEqualTo(anchorChunkId);
        assertThat(response.getSources().get(1).getExpansionOffset()).isEqualTo(1);
        assertThat(response.getSources().get(1).getEvidenceHits()).isEqualTo(2);
        assertThat(response.getSources().get(1).getMatchedSources()).containsExactlyInAnyOrder("vector", "keyword");
        assertThat(response.getSources().get(1).getSectionTitle()).isEqualTo("配置");
        assertThat(response.getSources().get(1).getSectionPath()).isEqualTo("手册/配置");
        assertThat(response.getSources().get(1).getChunkIndex()).isEqualTo(2);
        assertThat(response.getConversationCleanupStatus()).isEqualTo("ACTIVE");
        assertThat(response.getConversationExpiresAt()).isAfter(updatedAt);
        assertThat(response.getRetentionNotice()).isNull();
    }

    @Test
    void chat_buildsContextWithRoleLabels_andDeduplicatesChunkIds() {
        UUID kbId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID anchorChunkId = UUID.randomUUID();
        UUID duplicateChunkId = UUID.randomUUID();
        UUID secondDocumentId = UUID.randomUUID();

        ChatRequest request = new ChatRequest();
        request.setMessage("总结一下");
        request.setKbId(kbId);
        request.setConversationId(conversationId);

        Document anchor = new Document("anchor-content", Map.of(
                "chunk_id", anchorChunkId.toString(),
                "document_id", documentId.toString(),
                "section_path", "手册/配置",
                "retrieval_role", "anchor"
        ));
        Document sameSectionAdjacent = new Document("same-section", Map.of(
                "chunk_id", UUID.randomUUID().toString(),
                "document_id", documentId.toString(),
                "section_path", "手册/配置",
                "adjacent", true,
                "retrieval_role", "adjacent",
                "same_section", true
        ));
        Document diffSectionAdjacent = new Document("other-section", Map.of(
                "chunk_id", duplicateChunkId.toString(),
                "document_id", documentId.toString(),
                "section_path", "手册/其他",
                "adjacent", true,
                "retrieval_role", "adjacent",
                "same_section", false
        ));
        Document duplicate = new Document("duplicated-ignored", Map.of(
                "chunk_id", duplicateChunkId.toString(),
                "document_id", documentId.toString(),
                "section_path", "手册/其他",
                "adjacent", true,
                "retrieval_role", "adjacent",
                "same_section", false
        ));
        Document secondDocument = new Document("second-doc-content", Map.of(
                "chunk_id", UUID.randomUUID().toString(),
                "document_id", secondDocumentId.toString(),
                "section_path", "指南/概览",
                "retrieval_role", "anchor"
        ));

        LocalDateTime updatedAt = LocalDateTime.now().minusDays(2);
        mockConversationPersistence(conversationId, updatedAt);
        when(hybridSearchService.hybridSearch(eq("总结一下"), eq(kbId), any()))
                .thenReturn(List.of(anchor, sameSectionAdjacent, diffSectionAdjacent, duplicate, secondDocument));
        when(documentRepository.findAllById(any())).thenReturn(List.of(
                com.knowledgeforge.core.entity.Document.builder().id(documentId).title("配置手册").build(),
                com.knowledgeforge.core.entity.Document.builder().id(secondDocumentId).title("第二文档").build()
        ));
        when(credibilityService.calculateCredibility(any(), eq(kbId))).thenReturn(CredibilityBreakdownDTO.builder().overallScore(0.8).build());
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.chatResponse()).thenReturn(mockAiChatResponse("回答"));

        chatService.chat(request);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatClient).prompt(promptCaptor.capture());
        Prompt prompt = promptCaptor.getValue();
        String systemText = ((SystemMessage) prompt.getInstructions().getFirst()).getText();

        assertThat(systemText).contains("配置手册 / 手册/配置（主命中片段）");
        assertThat(systemText).contains("配置手册 / 手册/配置（同节补充片段）");
        assertThat(systemText).contains("配置手册 / 手册/其他（相邻补充片段）");
        assertThat(systemText).contains("第二文档 / 指南/概览（主命中片段）");
        assertThat(countOccurrences(systemText, "duplicated-ignored")).isZero();
        assertThat(countOccurrences(systemText, "other-section")).isEqualTo(1);
    }

    @Test
    void chat_sourcesRemainDeduplicated_whenExpandedChunksOverlap() {
        UUID kbId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID anchorChunkId = UUID.randomUUID();
        UUID adjacentChunkId = UUID.randomUUID();

        ChatRequest request = new ChatRequest();
        request.setMessage("总结配置");
        request.setKbId(kbId);
        request.setConversationId(conversationId);

        Document anchor = new Document("anchor-content", Map.of(
                "chunk_id", anchorChunkId.toString(),
                "document_id", documentId.toString(),
                "section_path", "手册/配置",
                "retrieval_role", "anchor",
                "anchor_chunk_id", anchorChunkId.toString(),
                "hybrid_score", 0.91
        ));
        Document adjacent = new Document("adjacent-content", Map.of(
                "chunk_id", adjacentChunkId.toString(),
                "document_id", documentId.toString(),
                "section_path", "手册/配置",
                "adjacent", true,
                "retrieval_role", "adjacent",
                "anchor_chunk_id", anchorChunkId.toString(),
                "expansion_offset", 1,
                "same_section", true,
                "vector_similarity", 0.73
        ));
        Document duplicateAdjacent = new Document("adjacent-duplicate", Map.of(
                "chunk_id", adjacentChunkId.toString(),
                "document_id", documentId.toString(),
                "section_path", "手册/配置",
                "adjacent", true,
                "retrieval_role", "adjacent",
                "anchor_chunk_id", anchorChunkId.toString(),
                "expansion_offset", 1,
                "same_section", true,
                "vector_similarity", 0.72
        ));

        LocalDateTime updatedAt = LocalDateTime.now().minusDays(2);
        mockConversationPersistence(conversationId, updatedAt);
        when(hybridSearchService.hybridSearch(eq("总结配置"), eq(kbId), any())).thenReturn(List.of(anchor, adjacent, duplicateAdjacent));
        when(documentRepository.findAllById(any())).thenReturn(List.of(
                com.knowledgeforge.core.entity.Document.builder().id(documentId).title("配置手册").build()
        ));
        when(credibilityService.calculateCredibility(any(), eq(kbId))).thenReturn(CredibilityBreakdownDTO.builder().overallScore(0.88).build());
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.chatResponse()).thenReturn(mockAiChatResponse("回答"));

        ChatResponse response = chatService.chat(request);

        assertThat(response.getSources()).hasSize(3);
        assertThat(response.getSources()).extracting(source -> source.getChunkId().toString())
                .containsExactly(anchorChunkId.toString(), adjacentChunkId.toString(), adjacentChunkId.toString());

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatClient).prompt(promptCaptor.capture());
        String systemText = ((SystemMessage) promptCaptor.getValue().getInstructions().getFirst()).getText();
        assertThat(countOccurrences(systemText, "adjacent-content")).isEqualTo(1);
        assertThat(countOccurrences(systemText, "adjacent-duplicate")).isZero();
    }

    @Test
    void chat_acceptsLegacyCardChunkId_whenBuildingSources() {
        UUID kbId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID anchorChunkId = UUID.randomUUID();
        LocalDateTime updatedAt = LocalDateTime.now().minusDays(1);

        ChatRequest request = new ChatRequest();
        request.setMessage("知识卡片是什么？");
        request.setKbId(kbId);
        request.setConversationId(conversationId);

        Document legacyCardDoc = new Document("知识卡片内容", Map.ofEntries(
                Map.entry("chunk_id", "card:" + anchorChunkId),
                Map.entry("document_id", documentId.toString()),
                Map.entry("retrieval_role", "anchor"),
                Map.entry("hybrid_score", 0.86)
        ));

        mockConversationPersistence(conversationId, updatedAt);
        when(hybridSearchService.hybridSearch(eq("知识卡片是什么？"), eq(kbId), any())).thenReturn(List.of(legacyCardDoc));
        when(documentRepository.findAllById(any())).thenReturn(List.of(
                com.knowledgeforge.core.entity.Document.builder().id(documentId).title("知识卡片文档").build()
        ));
        when(credibilityService.calculateCredibility(any(), eq(kbId))).thenReturn(CredibilityBreakdownDTO.builder().overallScore(0.77).build());
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.chatResponse()).thenReturn(mockAiChatResponse("回答"));

        ChatResponse response = chatService.chat(request);

        assertThat(response.getSources()).hasSize(1);
        assertThat(response.getSources().getFirst().getChunkId()).isEqualTo(anchorChunkId);
        assertThat(response.getSources().getFirst().getDocumentId()).isEqualTo(documentId);
    }

    @Test
    void chat_skipsMalformedDocumentId_whenBuildingSources() {
        UUID kbId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        LocalDateTime updatedAt = LocalDateTime.now().minusDays(1);

        ChatRequest request = new ChatRequest();
        request.setMessage("坏文档ID");
        request.setKbId(kbId);
        request.setConversationId(conversationId);

        Document malformedDoc = new Document("无法归属的内容", Map.ofEntries(
                Map.entry("chunk_id", UUID.randomUUID().toString()),
                Map.entry("document_id", "not-a-uuid"),
                Map.entry("retrieval_role", "anchor"),
                Map.entry("hybrid_score", 0.91)
        ));

        mockConversationPersistence(conversationId, updatedAt);
        when(hybridSearchService.hybridSearch(eq("坏文档ID"), eq(kbId), any())).thenReturn(List.of(malformedDoc));

        ChatResponse response = chatService.chat(request);

        assertThat(response.getSources()).isEmpty();
        assertThat(response.getAnswer()).contains("知识库中暂未找到");
    }

    @Test
    void chat_placesAllAnchorsBeforeAdjacentChunksInContext() {
        UUID kbId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID firstDocumentId = UUID.randomUUID();
        UUID secondDocumentId = UUID.randomUUID();
        UUID thirdDocumentId = UUID.randomUUID();

        ChatRequest request = new ChatRequest();
        request.setMessage("给我摘要");
        request.setKbId(kbId);
        request.setConversationId(conversationId);

        Document anchorOne = new Document("anchor-one", Map.of(
                "chunk_id", UUID.randomUUID().toString(),
                "document_id", firstDocumentId.toString(),
                "section_path", "手册/配置",
                "retrieval_role", "anchor"
        ));
        Document adjacent = new Document("adjacent-after-anchors", Map.of(
                "chunk_id", UUID.randomUUID().toString(),
                "document_id", firstDocumentId.toString(),
                "section_path", "手册/配置",
                "adjacent", true,
                "retrieval_role", "adjacent",
                "same_section", true
        ));
        Document anchorTwo = new Document("anchor-two", Map.of(
                "chunk_id", UUID.randomUUID().toString(),
                "document_id", secondDocumentId.toString(),
                "section_path", "手册/启动",
                "retrieval_role", "anchor"
        ));
        Document anchorThree = new Document("anchor-three", Map.of(
                "chunk_id", UUID.randomUUID().toString(),
                "document_id", thirdDocumentId.toString(),
                "section_path", "第二文档/概览",
                "retrieval_role", "anchor"
        ));

        LocalDateTime updatedAt = LocalDateTime.now().minusDays(2);
        mockConversationPersistence(conversationId, updatedAt);
        when(hybridSearchService.hybridSearch(eq("给我摘要"), eq(kbId), any()))
                .thenReturn(List.of(anchorOne, adjacent, anchorTwo, anchorThree));
        when(documentRepository.findAllById(any())).thenReturn(List.of(
                com.knowledgeforge.core.entity.Document.builder().id(firstDocumentId).title("配置手册").build(),
                com.knowledgeforge.core.entity.Document.builder().id(secondDocumentId).title("启动手册").build(),
                com.knowledgeforge.core.entity.Document.builder().id(thirdDocumentId).title("第二文档").build()
        ));
        when(credibilityService.calculateCredibility(any(), eq(kbId))).thenReturn(CredibilityBreakdownDTO.builder().overallScore(0.8).build());
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.chatResponse()).thenReturn(mockAiChatResponse("回答"));

        chatService.chat(request);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatClient).prompt(promptCaptor.capture());
        String systemText = ((SystemMessage) promptCaptor.getValue().getInstructions().getFirst()).getText();
        assertThat(systemText).contains("anchor-one");
        assertThat(systemText).contains("anchor-two");
        assertThat(systemText).contains("anchor-three");
        assertThat(systemText).contains("adjacent-after-anchors");
        assertThat(systemText.indexOf("anchor-one")).isLessThan(systemText.indexOf("adjacent-after-anchors"));
        assertThat(systemText.indexOf("anchor-two")).isLessThan(systemText.indexOf("adjacent-after-anchors"));
        assertThat(systemText.indexOf("anchor-three")).isLessThan(systemText.indexOf("adjacent-after-anchors"));
    }

    @Test
    void chat_prioritizesAnchorsWhenAdjacentsAppearEarlierInRetrievalOrder() {
        UUID kbId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID firstDocumentId = UUID.randomUUID();
        UUID secondDocumentId = UUID.randomUUID();
        UUID thirdDocumentId = UUID.randomUUID();
        UUID fourthDocumentId = UUID.randomUUID();

        ChatRequest request = new ChatRequest();
        request.setMessage("主命中优先");
        request.setKbId(kbId);
        request.setConversationId(conversationId);

        List<Document> docs = List.of(
                new Document("adjacent-first", metadata(firstDocumentId, "第一文档/补充", "adjacent", true, true)),
                new Document("anchor-one", metadata(firstDocumentId, "第一文档/主命中", "anchor")),
                new Document("adjacent-second", metadata(secondDocumentId, "第二文档/补充", "adjacent", true, false)),
                new Document("anchor-two", metadata(secondDocumentId, "第二文档/主命中", "anchor")),
                new Document("anchor-three", metadata(thirdDocumentId, "第三文档/主命中", "anchor")),
                new Document("adjacent-third", metadata(fourthDocumentId, "第四文档/补充", "adjacent", true, false)),
                new Document("adjacent-fourth", metadata(UUID.randomUUID(), "第五文档/补充", "adjacent", true, false)),
                new Document("adjacent-fifth", metadata(UUID.randomUUID(), "第六文档/补充", "adjacent", true, false)),
                new Document("adjacent-sixth", metadata(UUID.randomUUID(), "第七文档/补充", "adjacent", true, false))
        );

        LocalDateTime updatedAt = LocalDateTime.now().minusDays(2);
        mockConversationPersistence(conversationId, updatedAt);
        when(hybridSearchService.hybridSearch(eq("主命中优先"), eq(kbId), any())).thenReturn(docs);
        when(documentRepository.findAllById(any())).thenReturn(List.of(
                com.knowledgeforge.core.entity.Document.builder().id(firstDocumentId).title("第一文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(secondDocumentId).title("第二文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(thirdDocumentId).title("第三文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(fourthDocumentId).title("第四文档").build()
        ));
        when(credibilityService.calculateCredibility(any(), eq(kbId))).thenReturn(CredibilityBreakdownDTO.builder().overallScore(0.84).build());
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.chatResponse()).thenReturn(mockAiChatResponse("回答"));

        chatService.chat(request);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatClient).prompt(promptCaptor.capture());
        String systemText = ((SystemMessage) promptCaptor.getValue().getInstructions().getFirst()).getText();

        assertThat(systemText).contains("anchor-one");
        assertThat(systemText).contains("anchor-two");
        assertThat(systemText).contains("anchor-three");
        assertThat(systemText).contains("adjacent-first");
        assertThat(systemText).contains("adjacent-second");
        assertThat(systemText).contains("adjacent-third");
        assertThat(systemText).contains("adjacent-fourth");
        assertThat(systemText).contains("adjacent-fifth");
        assertThat(systemText).doesNotContain("adjacent-sixth");
        assertThat(systemText.indexOf("anchor-one")).isLessThan(systemText.indexOf("adjacent-first"));
        assertThat(systemText.indexOf("anchor-two")).isLessThan(systemText.indexOf("adjacent-first"));
        assertThat(systemText.indexOf("anchor-three")).isLessThan(systemText.indexOf("adjacent-first"));
        assertThat(countOccurrences(systemText, "【来源 ")).isEqualTo(8);
    }

    @Test
    void chat_prioritizesMultiEvidenceAnchors_inContextBeforeSingleEvidenceAnchors() {
        UUID kbId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID firstDocumentId = UUID.randomUUID();
        UUID secondDocumentId = UUID.randomUUID();
        UUID thirdDocumentId = UUID.randomUUID();
        UUID fourthDocumentId = UUID.randomUUID();

        ChatRequest request = new ChatRequest();
        request.setMessage("证据优先");
        request.setKbId(kbId);
        request.setConversationId(conversationId);

        List<Document> docs = List.of(
                new Document("single-evidence-early", Map.ofEntries(
                        Map.entry("chunk_id", UUID.randomUUID().toString()),
                        Map.entry("document_id", firstDocumentId.toString()),
                        Map.entry("section_path", "第一文档/主命中"),
                        Map.entry("retrieval_role", "anchor"),
                        Map.entry("evidence_hits", 1),
                        Map.entry("source_rank", 2),
                        Map.entry("original_order", 0)
                )),
                new Document("multi-evidence-late", Map.ofEntries(
                        Map.entry("chunk_id", UUID.randomUUID().toString()),
                        Map.entry("document_id", secondDocumentId.toString()),
                        Map.entry("section_path", "第二文档/主命中"),
                        Map.entry("retrieval_role", "anchor"),
                        Map.entry("evidence_hits", 2),
                        Map.entry("source_rank", 3),
                        Map.entry("original_order", 1)
                )),
                new Document("multi-evidence-best-rank", Map.ofEntries(
                        Map.entry("chunk_id", UUID.randomUUID().toString()),
                        Map.entry("document_id", thirdDocumentId.toString()),
                        Map.entry("section_path", "第三文档/主命中"),
                        Map.entry("retrieval_role", "anchor"),
                        Map.entry("evidence_hits", 2),
                        Map.entry("source_rank", 1),
                        Map.entry("original_order", 2)
                )),
                new Document("single-evidence-later", Map.ofEntries(
                        Map.entry("chunk_id", UUID.randomUUID().toString()),
                        Map.entry("document_id", fourthDocumentId.toString()),
                        Map.entry("section_path", "第四文档/主命中"),
                        Map.entry("retrieval_role", "anchor"),
                        Map.entry("evidence_hits", 1),
                        Map.entry("source_rank", 4),
                        Map.entry("original_order", 3)
                ))
        );

        LocalDateTime updatedAt = LocalDateTime.now().minusDays(2);
        mockConversationPersistence(conversationId, updatedAt);
        when(hybridSearchService.hybridSearch(eq("证据优先"), eq(kbId), any())).thenReturn(docs);
        when(documentRepository.findAllById(any())).thenReturn(List.of(
                com.knowledgeforge.core.entity.Document.builder().id(firstDocumentId).title("第一文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(secondDocumentId).title("第二文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(thirdDocumentId).title("第三文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(fourthDocumentId).title("第四文档").build()
        ));
        when(credibilityService.calculateCredibility(any(), eq(kbId))).thenReturn(CredibilityBreakdownDTO.builder().overallScore(0.85).build());
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.chatResponse()).thenReturn(mockAiChatResponse("回答"));

        chatService.chat(request);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatClient).prompt(promptCaptor.capture());
        String systemText = ((SystemMessage) promptCaptor.getValue().getInstructions().getFirst()).getText();

        assertThat(systemText.indexOf("multi-evidence-best-rank")).isLessThan(systemText.indexOf("single-evidence-early"));
        assertThat(systemText.indexOf("multi-evidence-late")).isLessThan(systemText.indexOf("single-evidence-early"));
        assertThat(systemText.indexOf("multi-evidence-best-rank")).isLessThan(systemText.indexOf("multi-evidence-late"));
        assertThat(systemText.indexOf("single-evidence-early")).isLessThan(systemText.indexOf("single-evidence-later"));
    }

    @Test
    void chat_limitsContextChunkCountPerDocument_andPreservesLaterDocuments() {
        UUID kbId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID firstDocumentId = UUID.randomUUID();
        UUID secondDocumentId = UUID.randomUUID();
        UUID thirdDocumentId = UUID.randomUUID();
        UUID fourthDocumentId = UUID.randomUUID();

        ChatRequest request = new ChatRequest();
        request.setMessage("控制上下文长度");
        request.setKbId(kbId);
        request.setConversationId(conversationId);

        List<Document> docs = List.of(
                new Document("doc1-anchor-1", metadata(firstDocumentId, "第一文档/1", "anchor")),
                new Document("doc1-anchor-2", metadata(firstDocumentId, "第一文档/2", "anchor")),
                new Document("doc1-adjacent-3", metadata(firstDocumentId, "第一文档/3", "adjacent", true, true)),
                new Document("doc1-skipped-4", metadata(firstDocumentId, "第一文档/4", "anchor")),
                new Document("doc2-anchor-1", metadata(secondDocumentId, "第二文档/1", "anchor")),
                new Document("doc2-anchor-2", metadata(secondDocumentId, "第二文档/2", "anchor")),
                new Document("doc2-anchor-3", metadata(secondDocumentId, "第二文档/3", "anchor")),
                new Document("doc2-skipped-4", metadata(secondDocumentId, "第二文档/4", "anchor")),
                new Document("doc3-anchor-1", metadata(thirdDocumentId, "第三文档/1", "anchor")),
                new Document("doc4-anchor-1", metadata(fourthDocumentId, "第四文档/1", "anchor")),
                new Document("doc4-over-global-limit", metadata(fourthDocumentId, "第四文档/2", "anchor")),
                new Document("doc1-adjacent-overflow", metadata(firstDocumentId, "第一文档/5", "adjacent", true, false)),
                new Document("doc2-adjacent-overflow", metadata(secondDocumentId, "第二文档/5", "adjacent", true, false))
        );

        LocalDateTime updatedAt = LocalDateTime.now().minusDays(2);
        mockConversationPersistence(conversationId, updatedAt);
        when(hybridSearchService.hybridSearch(eq("控制上下文长度"), eq(kbId), any())).thenReturn(docs);
        when(documentRepository.findAllById(any())).thenReturn(List.of(
                com.knowledgeforge.core.entity.Document.builder().id(firstDocumentId).title("第一文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(secondDocumentId).title("第二文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(thirdDocumentId).title("第三文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(fourthDocumentId).title("第四文档").build()
        ));
        when(credibilityService.calculateCredibility(any(), eq(kbId))).thenReturn(CredibilityBreakdownDTO.builder().overallScore(0.86).build());
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.chatResponse()).thenReturn(mockAiChatResponse("回答"));

        chatService.chat(request);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatClient).prompt(promptCaptor.capture());
        String systemText = ((SystemMessage) promptCaptor.getValue().getInstructions().getFirst()).getText();

        assertThat(systemText).contains("doc1-anchor-1");
        assertThat(systemText).contains("doc1-anchor-2");
        assertThat(systemText).contains("doc1-skipped-4");
        assertThat(systemText).doesNotContain("doc1-adjacent-3");
        assertThat(systemText).contains("doc2-anchor-1");
        assertThat(systemText).contains("doc2-anchor-2");
        assertThat(systemText).contains("doc2-anchor-3");
        assertThat(systemText).doesNotContain("doc2-skipped-4");
        assertThat(systemText).contains("doc3-anchor-1");
        assertThat(systemText).contains("doc4-anchor-1");
        assertThat(systemText).doesNotContain("doc4-over-global-limit");
        assertThat(systemText).doesNotContain("doc1-adjacent-overflow");
        assertThat(systemText).doesNotContain("doc2-adjacent-overflow");
        assertThat(countOccurrences(systemText, "【来源 ")).isEqualTo(8);
    }

    @Test
    void chat_doesNotSpendChunkBudgetOnDuplicatesOrInvalidDocuments() {
        UUID kbId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID firstDocumentId = UUID.randomUUID();
        UUID secondDocumentId = UUID.randomUUID();
        UUID duplicateChunkId = UUID.randomUUID();

        ChatRequest request = new ChatRequest();
        request.setMessage("预算顺序");
        request.setKbId(kbId);
        request.setConversationId(conversationId);

        Document duplicateOne = new Document("duplicate-one", Map.of(
                "chunk_id", duplicateChunkId.toString(),
                "document_id", firstDocumentId.toString(),
                "section_path", "第一文档/配置",
                "retrieval_role", "anchor"
        ));
        Document duplicateTwo = new Document("duplicate-two", Map.of(
                "chunk_id", duplicateChunkId.toString(),
                "document_id", firstDocumentId.toString(),
                "section_path", "第一文档/配置",
                "retrieval_role", "anchor"
        ));
        Document missingDocumentId = new Document("missing-document-id", Map.of(
                "chunk_id", UUID.randomUUID().toString(),
                "section_path", "无文档"
        ));
        Document malformedDocumentId = new Document("malformed-document-id", Map.of(
                "chunk_id", UUID.randomUUID().toString(),
                "document_id", "not-a-uuid",
                "section_path", "坏数据"
        ));
        Document validSecond = new Document("valid-second", Map.of(
                "chunk_id", UUID.randomUUID().toString(),
                "document_id", secondDocumentId.toString(),
                "section_path", "第二文档/概览",
                "retrieval_role", "anchor"
        ));

        LocalDateTime updatedAt = LocalDateTime.now().minusDays(2);
        mockConversationPersistence(conversationId, updatedAt);
        when(hybridSearchService.hybridSearch(eq("预算顺序"), eq(kbId), any()))
                .thenReturn(List.of(duplicateOne, duplicateTwo, missingDocumentId, malformedDocumentId, validSecond));
        when(documentRepository.findAllById(any())).thenReturn(List.of(
                com.knowledgeforge.core.entity.Document.builder().id(firstDocumentId).title("第一文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(secondDocumentId).title("第二文档").build()
        ));
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.chatResponse()).thenReturn(mockAiChatResponse("回答"));

        chatService.chat(request);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatClient).prompt(promptCaptor.capture());
        String systemText = ((SystemMessage) promptCaptor.getValue().getInstructions().getFirst()).getText();

        assertThat(countOccurrences(systemText, "duplicate-one")).isEqualTo(1);
        assertThat(systemText).doesNotContain("duplicate-two");
        assertThat(systemText).doesNotContain("missing-document-id");
        assertThat(systemText).doesNotContain("malformed-document-id");
        assertThat(systemText).contains("valid-second");
        assertThat(countOccurrences(systemText, "【来源 ")).isEqualTo(2);
    }

    @Test
    void chat_prefersHighConfidenceAnchors_beforeFallbackAnchorsUnderBudget() {
        UUID kbId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID firstDocumentId = UUID.randomUUID();
        UUID secondDocumentId = UUID.randomUUID();
        UUID thirdDocumentId = UUID.randomUUID();
        UUID fourthDocumentId = UUID.randomUUID();
        UUID fifthDocumentId = UUID.randomUUID();
        UUID sixthDocumentId = UUID.randomUUID();
        UUID seventhDocumentId = UUID.randomUUID();
        UUID eighthDocumentId = UUID.randomUUID();
        UUID ninthDocumentId = UUID.randomUUID();

        ChatRequest request = new ChatRequest();
        request.setMessage("预算优先级");
        request.setKbId(kbId);
        request.setConversationId(conversationId);

        List<Document> docs = List.of(
                anchorWithEvidence(firstDocumentId, "fallback-1", "第一文档/主命中", 1, 1, 0),
                anchorWithEvidence(secondDocumentId, "fallback-2", "第二文档/主命中", 1, 2, 1),
                anchorWithEvidence(thirdDocumentId, "fallback-3", "第三文档/主命中", 1, 3, 2),
                anchorWithEvidence(fourthDocumentId, "fallback-4", "第四文档/主命中", 1, 4, 3),
                anchorWithEvidence(fifthDocumentId, "fallback-5", "第五文档/主命中", 1, 5, 4),
                anchorWithEvidence(sixthDocumentId, "fallback-6", "第六文档/主命中", 1, 6, 5),
                anchorWithEvidence(seventhDocumentId, "fallback-7", "第七文档/主命中", 1, 7, 6),
                anchorWithEvidence(eighthDocumentId, "high-confidence-1", "第八文档/主命中", 2, 8, 7),
                anchorWithEvidence(ninthDocumentId, "high-confidence-2", "第九文档/主命中", 2, 9, 8)
        );

        LocalDateTime updatedAt = LocalDateTime.now().minusDays(2);
        mockConversationPersistence(conversationId, updatedAt);
        when(hybridSearchService.hybridSearch(eq("预算优先级"), eq(kbId), any())).thenReturn(docs);
        when(documentRepository.findAllById(any())).thenReturn(List.of(
                com.knowledgeforge.core.entity.Document.builder().id(firstDocumentId).title("第一文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(secondDocumentId).title("第二文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(thirdDocumentId).title("第三文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(fourthDocumentId).title("第四文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(fifthDocumentId).title("第五文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(sixthDocumentId).title("第六文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(seventhDocumentId).title("第七文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(eighthDocumentId).title("第八文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(ninthDocumentId).title("第九文档").build()
        ));
        when(credibilityService.calculateCredibility(any(), eq(kbId))).thenReturn(CredibilityBreakdownDTO.builder().overallScore(0.83).build());
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.chatResponse()).thenReturn(mockAiChatResponse("回答"));

        chatService.chat(request);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatClient).prompt(promptCaptor.capture());
        String systemText = ((SystemMessage) promptCaptor.getValue().getInstructions().getFirst()).getText();

        assertThat(systemText).contains("high-confidence-1");
        assertThat(systemText).contains("high-confidence-2");
        assertThat(systemText).doesNotContain("fallback-7");
        assertThat(systemText.indexOf("high-confidence-1")).isLessThan(systemText.indexOf("fallback-1"));
        assertThat(systemText.indexOf("high-confidence-2")).isLessThan(systemText.indexOf("fallback-1"));
        assertThat(countOccurrences(systemText, "【来源 ")).isEqualTo(8);
    }

    @Test
    void chat_treatsStrongSingleSignalAnchorsAsHighConfidence_forContextBudgeting() {
        UUID kbId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID fallbackDocId = UUID.randomUUID();
        UUID strongVectorDocId = UUID.randomUUID();
        UUID strongKeywordDocId = UUID.randomUUID();
        UUID strongGraphDocId = UUID.randomUUID();
        UUID fallbackDocId2 = UUID.randomUUID();
        UUID fallbackDocId3 = UUID.randomUUID();
        UUID fallbackDocId4 = UUID.randomUUID();
        UUID fallbackDocId5 = UUID.randomUUID();
        UUID fallbackDocId6 = UUID.randomUUID();

        ChatRequest request = new ChatRequest();
        request.setMessage("高置信混合规则");
        request.setKbId(kbId);
        request.setConversationId(conversationId);

        List<Document> docs = List.of(
                anchorWithScores(fallbackDocId, "fallback-1", "第一文档/主命中", 1, 1, 0, 0.0106, 0.62, 0.0, 0.0),
                anchorWithScores(strongVectorDocId, "strong-vector", "第二文档/主命中", 1, 2, 1, 0.0112, 0.93, 0.0, 0.0),
                anchorWithScores(strongKeywordDocId, "strong-keyword", "第三文档/主命中", 1, 3, 2, 0.0117, 0.0, 0.31, 0.0),
                anchorWithScores(strongGraphDocId, "strong-graph", "第四文档/主命中", 1, 4, 3, 0.0108, 0.0, 0.0, 1.0),
                anchorWithScores(fallbackDocId2, "fallback-2", "第五文档/主命中", 1, 5, 4, 0.0105, 0.61, 0.0, 0.0),
                anchorWithScores(fallbackDocId3, "fallback-3", "第六文档/主命中", 1, 6, 5, 0.0104, 0.6, 0.0, 0.0),
                anchorWithScores(fallbackDocId4, "fallback-4", "第七文档/主命中", 1, 7, 6, 0.0103, 0.59, 0.0, 0.0),
                anchorWithScores(fallbackDocId5, "fallback-5", "第八文档/主命中", 1, 8, 7, 0.0102, 0.58, 0.0, 0.0),
                anchorWithScores(fallbackDocId6, "fallback-6", "第九文档/主命中", 1, 9, 8, 0.0101, 0.57, 0.0, 0.0)
        );

        LocalDateTime updatedAt = LocalDateTime.now().minusDays(2);
        mockConversationPersistence(conversationId, updatedAt);
        when(hybridSearchService.hybridSearch(eq("高置信混合规则"), eq(kbId), any())).thenReturn(docs);
        when(documentRepository.findAllById(any())).thenReturn(List.of(
                com.knowledgeforge.core.entity.Document.builder().id(fallbackDocId).title("第一文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(strongVectorDocId).title("第二文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(strongKeywordDocId).title("第三文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(strongGraphDocId).title("第四文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(fallbackDocId2).title("第五文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(fallbackDocId3).title("第六文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(fallbackDocId4).title("第七文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(fallbackDocId5).title("第八文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(fallbackDocId6).title("第九文档").build()
        ));
        when(credibilityService.calculateCredibility(any(), eq(kbId))).thenReturn(CredibilityBreakdownDTO.builder().overallScore(0.86).build());
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.chatResponse()).thenReturn(mockAiChatResponse("回答"));

        chatService.chat(request);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatClient).prompt(promptCaptor.capture());
        String systemText = ((SystemMessage) promptCaptor.getValue().getInstructions().getFirst()).getText();

        assertThat(systemText).contains("strong-vector");
        assertThat(systemText).contains("strong-keyword");
        assertThat(systemText).contains("strong-graph");
        assertThat(systemText).doesNotContain("fallback-6");
        assertThat(systemText.indexOf("strong-vector")).isLessThan(systemText.indexOf("fallback-1"));
        assertThat(systemText.indexOf("strong-keyword")).isLessThan(systemText.indexOf("fallback-1"));
        assertThat(systemText.indexOf("strong-graph")).isLessThan(systemText.indexOf("fallback-1"));
        assertThat(countOccurrences(systemText, "【来源 ")).isEqualTo(8);
    }

    @Test
    void chat_doesNotPromoteWeakSingleSignalAnchorsIntoHighConfidencePool() {
        UUID kbId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID multiEvidenceDocId = UUID.randomUUID();
        UUID weakVectorDocId = UUID.randomUUID();
        UUID weakKeywordDocId = UUID.randomUUID();
        UUID weakGraphDocId = UUID.randomUUID();
        UUID fallbackDocId1 = UUID.randomUUID();
        UUID fallbackDocId2 = UUID.randomUUID();
        UUID fallbackDocId3 = UUID.randomUUID();
        UUID fallbackDocId4 = UUID.randomUUID();
        UUID fallbackDocId5 = UUID.randomUUID();

        ChatRequest request = new ChatRequest();
        request.setMessage("弱单路不升级");
        request.setKbId(kbId);
        request.setConversationId(conversationId);

        List<Document> docs = List.of(
                anchorWithScores(weakVectorDocId, "weak-vector", "第一文档/主命中", 1, 1, 0, 0.0109, 0.89, 0.0, 0.0),
                anchorWithScores(weakKeywordDocId, "weak-keyword", "第二文档/主命中", 1, 2, 1, 0.0114, 0.0, 0.27, 0.0),
                anchorWithScores(weakGraphDocId, "weak-graph", "第三文档/主命中", 1, 3, 2, 0.0104, 0.0, 0.0, 0.89),
                anchorWithScores(multiEvidenceDocId, "multi-evidence", "第四文档/主命中", 2, 4, 3, 0.0116, 0.75, 0.29, 0.0),
                anchorWithScores(fallbackDocId1, "fallback-1", "第五文档/主命中", 1, 5, 4, 0.0105, 0.62, 0.0, 0.0),
                anchorWithScores(fallbackDocId2, "fallback-2", "第六文档/主命中", 1, 6, 5, 0.0104, 0.61, 0.0, 0.0),
                anchorWithScores(fallbackDocId3, "fallback-3", "第七文档/主命中", 1, 7, 6, 0.0103, 0.6, 0.0, 0.0),
                anchorWithScores(fallbackDocId4, "fallback-4", "第八文档/主命中", 1, 8, 7, 0.0102, 0.59, 0.0, 0.0),
                anchorWithScores(fallbackDocId5, "fallback-5", "第九文档/主命中", 1, 9, 8, 0.0101, 0.58, 0.0, 0.0)
        );

        LocalDateTime updatedAt = LocalDateTime.now().minusDays(2);
        mockConversationPersistence(conversationId, updatedAt);
        when(hybridSearchService.hybridSearch(eq("弱单路不升级"), eq(kbId), any())).thenReturn(docs);
        when(documentRepository.findAllById(any())).thenReturn(List.of(
                com.knowledgeforge.core.entity.Document.builder().id(weakVectorDocId).title("第一文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(weakKeywordDocId).title("第二文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(weakGraphDocId).title("第三文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(multiEvidenceDocId).title("第四文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(fallbackDocId1).title("第五文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(fallbackDocId2).title("第六文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(fallbackDocId3).title("第七文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(fallbackDocId4).title("第八文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(fallbackDocId5).title("第九文档").build()
        ));
        when(credibilityService.calculateCredibility(any(), eq(kbId))).thenReturn(CredibilityBreakdownDTO.builder().overallScore(0.84).build());
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.chatResponse()).thenReturn(mockAiChatResponse("回答"));

        chatService.chat(request);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatClient).prompt(promptCaptor.capture());
        String systemText = ((SystemMessage) promptCaptor.getValue().getInstructions().getFirst()).getText();

        assertThat(systemText).contains("multi-evidence");
        assertThat(systemText.indexOf("multi-evidence")).isLessThan(systemText.indexOf("weak-vector"));
        assertThat(systemText.indexOf("multi-evidence")).isLessThan(systemText.indexOf("weak-keyword"));
        assertThat(systemText.indexOf("multi-evidence")).isLessThan(systemText.indexOf("weak-graph"));
    }

    @Test
    void chat_usesOverriddenHighConfidenceThresholdsFromProperties() {
        UUID kbId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID multiEvidenceDocId = UUID.randomUUID();
        UUID vectorDocId = UUID.randomUUID();
        UUID fallbackDocId1 = UUID.randomUUID();
        UUID fallbackDocId2 = UUID.randomUUID();
        UUID fallbackDocId3 = UUID.randomUUID();
        UUID fallbackDocId4 = UUID.randomUUID();
        UUID fallbackDocId5 = UUID.randomUUID();
        UUID fallbackDocId6 = UUID.randomUUID();
        UUID fallbackDocId7 = UUID.randomUUID();
        UUID fallbackDocId8 = UUID.randomUUID();

        chatContextProperties.getHighConfidence().setVectorSimilarityThreshold(0.95);

        ChatRequest request = new ChatRequest();
        request.setMessage("配置覆盖高置信阈值");
        request.setKbId(kbId);
        request.setConversationId(conversationId);

        List<Document> docs = List.of(
                anchorWithScores(vectorDocId, "vector-no-longer-high-confidence", "第一文档/主命中", 1, 1, 0, 0.0112, 0.93, 0.0, 0.0),
                anchorWithScores(multiEvidenceDocId, "multi-evidence-still-high-confidence", "第二文档/主命中", 2, 2, 1, 0.0116, 0.75, 0.29, 0.0),
                anchorWithScores(fallbackDocId1, "fallback-1", "第三文档/主命中", 1, 3, 2, 0.0109, 0.62, 0.0, 0.0),
                anchorWithScores(fallbackDocId2, "fallback-2", "第四文档/主命中", 1, 4, 3, 0.0108, 0.61, 0.0, 0.0),
                anchorWithScores(fallbackDocId3, "fallback-3", "第五文档/主命中", 1, 5, 4, 0.0107, 0.60, 0.0, 0.0),
                anchorWithScores(fallbackDocId4, "fallback-4", "第六文档/主命中", 1, 6, 5, 0.0106, 0.59, 0.0, 0.0),
                anchorWithScores(fallbackDocId5, "fallback-5", "第七文档/主命中", 1, 7, 6, 0.0105, 0.58, 0.0, 0.0),
                anchorWithScores(fallbackDocId6, "fallback-6", "第八文档/主命中", 1, 8, 7, 0.0104, 0.57, 0.0, 0.0),
                anchorWithScores(fallbackDocId7, "fallback-7", "第九文档/主命中", 1, 9, 8, 0.0103, 0.56, 0.0, 0.0),
                anchorWithScores(fallbackDocId8, "fallback-8", "第十文档/主命中", 1, 10, 9, 0.0102, 0.55, 0.0, 0.0)
        );

        LocalDateTime updatedAt = LocalDateTime.now().minusDays(2);
        mockConversationPersistence(conversationId, updatedAt);
        when(hybridSearchService.hybridSearch(eq("配置覆盖高置信阈值"), eq(kbId), any())).thenReturn(docs);
        when(documentRepository.findAllById(any())).thenReturn(List.of(
                com.knowledgeforge.core.entity.Document.builder().id(vectorDocId).title("第一文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(multiEvidenceDocId).title("第二文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(fallbackDocId1).title("第三文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(fallbackDocId2).title("第四文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(fallbackDocId3).title("第五文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(fallbackDocId4).title("第六文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(fallbackDocId5).title("第七文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(fallbackDocId6).title("第八文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(fallbackDocId7).title("第九文档").build(),
                com.knowledgeforge.core.entity.Document.builder().id(fallbackDocId8).title("第十文档").build()
        ));
        when(credibilityService.calculateCredibility(any(), eq(kbId))).thenReturn(CredibilityBreakdownDTO.builder().overallScore(0.82).build());
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.chatResponse()).thenReturn(mockAiChatResponse("回答"));

        chatService.chat(request);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatClient).prompt(promptCaptor.capture());
        String systemText = ((SystemMessage) promptCaptor.getValue().getInstructions().getFirst()).getText();

        assertThat(systemText).contains("multi-evidence-still-high-confidence");
        assertThat(systemText).doesNotContain("fallback-8");
        assertThat(systemText.indexOf("multi-evidence-still-high-confidence"))
                .isLessThan(systemText.indexOf("fallback-1"));
        assertThat(systemText.indexOf("vector-no-longer-high-confidence"))
                .isLessThan(systemText.indexOf("fallback-1"));
    }

    @Test
    void chat_normalizesAndTruncatesSourceSnippets() {
        UUID kbId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID secondDocumentId = UUID.randomUUID();

        ChatRequest request = new ChatRequest();
        request.setMessage("来源摘要");
        request.setKbId(kbId);
        request.setConversationId(conversationId);

        Document normalized = new Document("  第一行\n\n第二行\t第三行  ", Map.of(
                "chunk_id", UUID.randomUUID().toString(),
                "document_id", documentId.toString(),
                "section_path", "来源/一",
                "retrieval_role", "anchor",
                "hybrid_score", 0.91
        ));
        Document truncated = new Document("alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron pi rho sigma tau upsilon phi chi psi omega ".repeat(2), Map.of(
                "chunk_id", UUID.randomUUID().toString(),
                "document_id", secondDocumentId.toString(),
                "section_path", "来源/二",
                "retrieval_role", "anchor",
                "hybrid_score", 0.88
        ));

        LocalDateTime updatedAt = LocalDateTime.now().minusDays(2);
        mockConversationPersistence(conversationId, updatedAt);
        when(hybridSearchService.hybridSearch(eq("来源摘要"), eq(kbId), any())).thenReturn(List.of(normalized, truncated));
        when(documentRepository.findAllById(any())).thenReturn(List.of(
                com.knowledgeforge.core.entity.Document.builder().id(documentId).title("来源一").build(),
                com.knowledgeforge.core.entity.Document.builder().id(secondDocumentId).title("来源二").build()
        ));
        when(credibilityService.calculateCredibility(any(), eq(kbId))).thenReturn(CredibilityBreakdownDTO.builder().overallScore(0.9).build());
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.chatResponse()).thenReturn(mockAiChatResponse("回答"));

        ChatResponse response = chatService.chat(request);

        assertThat(response.getSources()).hasSize(2);
        assertThat(response.getSources().get(0).getSnippet()).isEqualTo("第一行 第二行 第三行");
        assertThat(response.getSources().get(0).getSnippet()).doesNotEndWith("...");
        assertThat(response.getSources().get(1).getSnippet()).endsWith("...");
        assertThat(response.getSources().get(1).getSnippet()).doesNotContain("\n");
        assertThat(response.getSources().get(1).getSnippet()).doesNotContain("  ");
        assertThat(response.getSources().get(1).getSnippet()).doesNotContain(" ...");
        assertThat(response.getSources().get(1).getSnippet().length()).isLessThanOrEqualTo(153);
    }

    @Test
    void chat_triggersKnowledgeExtractionWithConfiguredAutoExtractMaxCards() {
        UUID kbId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        LocalDateTime updatedAt = LocalDateTime.now().minusDays(2);

        chatExtractionProperties.setAutoExtractMaxCards(5);

        ChatRequest request = new ChatRequest();
        request.setMessage("触发提卡");
        request.setKbId(kbId);
        request.setConversationId(conversationId);

        mockConversationPersistence(conversationId, updatedAt);
        when(hybridSearchService.hybridSearch(eq("触发提卡"), eq(kbId), any())).thenReturn(List.of());

        ChatResponse response = chatService.chat(request);

        assertThat(response.getAnswer()).contains("知识库中暂未找到");
        verify(knowledgeExtractionService, timeout(1000))
                .extractFromConversation(eq(kbId), eq(conversationId), eq(5));
    }

    @Test
    void chat_usesKbIdsPath_whenMultipleKnowledgeBasesProvided() {
        UUID conversationId = UUID.randomUUID();
        UUID kb1 = UUID.randomUUID();
        UUID kb2 = UUID.randomUUID();
        LocalDateTime updatedAt = LocalDateTime.now().minusDays(24);

        ChatRequest request = new ChatRequest();
        request.setMessage("跨库问题");
        request.setConversationId(conversationId);
        request.setKbIds(List.of(kb1, kb2));

        mockConversationPersistence(conversationId, updatedAt);
        when(hybridSearchService.hybridSearchMultipleKbs(eq("跨库问题"), eq(List.of(kb1, kb2)), any())).thenReturn(List.of());

        ChatResponse response = chatService.chat(request);

        assertThat(response.getAnswer()).contains("知识库中暂未找到");
        assertThat(response.getConversationCleanupStatus()).isEqualTo("EXPIRING");
        assertThat(response.getConversationExpiresAt()).isAfter(updatedAt);
        assertThat(response.getRetentionNotice()).contains("自动清理");
        verify(hybridSearchService).hybridSearchMultipleKbs(eq("跨库问题"), eq(List.of(kb1, kb2)), any());
        verify(hybridSearchService, never()).hybridSearch(any(), any(), any());
    }

    @Test
    void chat_recordsCoreObservabilityMetrics_onSuccessfulSyncPath() {
        UUID kbId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        LocalDateTime updatedAt = LocalDateTime.now().minusDays(2);

        ChatRequest request = new ChatRequest();
        request.setMessage("帮我总结配置");
        request.setKbId(kbId);
        request.setConversationId(conversationId);

        Document anchor = new Document("anchor-content", Map.of(
                "chunk_id", UUID.randomUUID().toString(),
                "document_id", documentId.toString(),
                "section_path", "手册/配置",
                "retrieval_role", "anchor",
                "hybrid_score", 0.91
        ));

        mockConversationPersistence(conversationId, updatedAt);
        when(hybridSearchService.hybridSearch(eq("帮我总结配置"), eq(kbId), any())).thenReturn(List.of(anchor));
        when(documentRepository.findAllById(any())).thenReturn(List.of(
                com.knowledgeforge.core.entity.Document.builder().id(documentId).title("配置手册").build()
        ));
        when(credibilityService.calculateCredibility(any(), eq(kbId)))
                .thenReturn(CredibilityBreakdownDTO.builder().overallScore(0.88).build());
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.chatResponse()).thenReturn(mockAiChatResponse("这是同步回答"));

        chatService.chat(request);

        assertThat(ragMetricsAggregator.getSnapshot("rag.retrieval.total")).isNotNull();
        assertThat(ragMetricsAggregator.getSnapshot("rag.context.build")).isNotNull();
        assertThat(ragMetricsAggregator.getSnapshot("rag.sources.build")).isNotNull();
        assertThat(ragMetricsAggregator.getSnapshot("rag.llm.call")).isNotNull();
        assertThat(ragMetricsAggregator.getSnapshot("rag.sync.full_response")).isNotNull();
    }

    @Test
    void chat_rejectsExpiredConversation() {
        UUID conversationId = UUID.randomUUID();
        ChatRequest request = new ChatRequest();
        request.setMessage("继续");
        request.setConversationId(conversationId);

        Conversation expiredConversation = Conversation.builder()
                .id(conversationId)
                .title("已过期会话")
                .updatedAt(LocalDateTime.now().minusDays(31))
                .deleted(false)
                .build();
        when(conversationRepository.findByIdAndDeletedFalse(conversationId))
                .thenReturn(java.util.Optional.of(expiredConversation));

        assertThatThrownBy(() -> chatService.chat(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("对话已过期");

        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }

    private Conversation mockConversationPersistence(UUID conversationId, LocalDateTime updatedAt) {
        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .title("测试会话")
                .updatedAt(updatedAt)
                .deleted(false)
                .build();
        when(conversationRepository.findByIdAndDeletedFalse(conversationId)).thenReturn(java.util.Optional.of(conversation));
        when(conversationRepository.findById(conversationId)).thenReturn(java.util.Optional.of(conversation));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            if (message.getId() == null) {
                message.setId(UUID.randomUUID());
            }
            return message;
        });
        return conversation;
    }

    private org.springframework.ai.chat.model.ChatResponse mockAiChatResponse(String text) {
        org.springframework.ai.chat.messages.AssistantMessage assistantMessage = new org.springframework.ai.chat.messages.AssistantMessage(text);
        org.springframework.ai.chat.model.Generation generation = new org.springframework.ai.chat.model.Generation(assistantMessage);
        return org.springframework.ai.chat.model.ChatResponse.builder()
                .generations(List.of(generation))
                .build();
    }

    private String longText(String prefix) {
        return prefix + "-" + "x".repeat(180);
    }

    private Map<String, Object> metadata(UUID documentId, String sectionPath, String retrievalRole) {
        return Map.of(
                "chunk_id", UUID.randomUUID().toString(),
                "document_id", documentId.toString(),
                "section_path", sectionPath,
                "retrieval_role", retrievalRole
        );
    }

    private Map<String, Object> metadata(UUID documentId, String sectionPath, String retrievalRole,
                                         boolean adjacent, boolean sameSection) {
        return Map.of(
                "chunk_id", UUID.randomUUID().toString(),
                "document_id", documentId.toString(),
                "section_path", sectionPath,
                "retrieval_role", retrievalRole,
                "adjacent", adjacent,
                "same_section", sameSection
        );
    }

    private Document anchorWithEvidence(UUID documentId, String text, String sectionPath,
                                        int evidenceHits, int sourceRank, int originalOrder) {
        return anchorWithScores(documentId, text, sectionPath, evidenceHits, sourceRank, originalOrder,
                0.0, 0.0, 0.0, 0.0);
    }

    private Document anchorWithScores(UUID documentId, String text, String sectionPath,
                                      int evidenceHits, int sourceRank, int originalOrder,
                                      double hybridScore, double vectorSimilarity,
                                      double keywordScore, double graphScore) {
        java.util.Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("chunk_id", UUID.randomUUID().toString());
        metadata.put("document_id", documentId.toString());
        metadata.put("section_path", sectionPath);
        metadata.put("retrieval_role", "anchor");
        metadata.put("evidence_hits", evidenceHits);
        metadata.put("source_rank", sourceRank);
        metadata.put("original_order", originalOrder);
        if (hybridScore > 0.0) {
            metadata.put("hybrid_score", hybridScore);
        }
        if (vectorSimilarity > 0.0) {
            metadata.put("vector_similarity", vectorSimilarity);
        }
        if (keywordScore > 0.0) {
            metadata.put("keyword_score", keywordScore);
        }
        if (graphScore > 0.0) {
            metadata.put("graph_score", graphScore);
        }
        return new Document(text, metadata);
    }

    private int countOccurrences(String text, String fragment) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(fragment, index)) >= 0) {
            count++;
            index += fragment.length();
        }
        return count;
    }
}
