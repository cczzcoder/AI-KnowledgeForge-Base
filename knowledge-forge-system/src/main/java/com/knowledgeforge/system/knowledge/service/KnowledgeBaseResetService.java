package com.knowledgeforge.system.knowledge.service;

import com.knowledgeforge.core.entity.Conversation;
import com.knowledgeforge.core.entity.Document;
import com.knowledgeforge.core.entity.KnowledgeBase;
import com.knowledgeforge.system.conversation.repository.ChatMessageRepository;
import com.knowledgeforge.system.conversation.repository.ConversationRepository;
import com.knowledgeforge.system.conversation.service.ConversationService;
import com.knowledgeforge.system.graph.service.GraphService;
import com.knowledgeforge.system.knowledge.dto.DocumentDTO;
import com.knowledgeforge.system.knowledge.dto.KnowledgeBaseDTO;
import com.knowledgeforge.system.knowledge.dto.KnowledgeBaseResetRequest;
import com.knowledgeforge.system.knowledge.repository.DocumentChunkRepository;
import com.knowledgeforge.system.knowledge.repository.DocumentRepository;
import com.knowledgeforge.system.knowledge.repository.KnowledgeBaseRepository;
import com.knowledgeforge.system.knowledgecard.repository.KnowledgeCardRepository;
import com.knowledgeforge.system.objectstore.MinioService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseResetService {

    private static final Duration DOCUMENT_READY_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DOCUMENT_READY_POLL_INTERVAL = Duration.ofMillis(300);

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final KnowledgeCardRepository knowledgeCardRepository;
    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final GraphService graphService;
    private final JdbcTemplate jdbcTemplate;
    private final MinioService minioService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final DocumentService documentService;
    private final ConversationService conversationService;

    @Transactional
    public ResetCleanupStats hardDeleteAllKnowledgeBases() {
        List<KnowledgeBase> knowledgeBases = knowledgeBaseRepository.findAll();
        ResetCleanupStats stats = new ResetCleanupStats();
        List<String> minioPaths = new ArrayList<>();

        for (KnowledgeBase kb : knowledgeBases) {
            UUID kbId = kb.getId();
            List<Document> documents = documentRepository.findByKbId(kbId);
            List<UUID> documentIds = documents.stream().map(Document::getId).toList();
            List<Conversation> conversations = conversationRepository.findByKbId(kbId);
            List<UUID> conversationIds = conversations.stream().map(Conversation::getId).toList();
            long knowledgeCardCount = knowledgeCardRepository.findAllByKbId(kbId).size();

            stats.setKnowledgeBasesDeleted(stats.getKnowledgeBasesDeleted() + 1);
            stats.setDocumentsDeleted(stats.getDocumentsDeleted() + documents.size());
            stats.setDocumentChunksDeleted(stats.getDocumentChunksDeleted() + countDocumentChunks(documentIds));
            stats.setConversationsDeleted(stats.getConversationsDeleted() + conversations.size());
            stats.setKnowledgeCardsDeleted(stats.getKnowledgeCardsDeleted() + knowledgeCardCount);

            for (Document document : documents) {
                if (document.getFilePath() != null && !document.getFilePath().isBlank()) {
                    minioPaths.add(document.getFilePath());
                }
            }

            stats.setDocumentVectorsDeleted(stats.getDocumentVectorsDeleted() + deleteDocumentVectorsByKbId(kbId));
            stats.setKnowledgeCardVectorsDeleted(stats.getKnowledgeCardVectorsDeleted() + deleteKnowledgeCardVectorsByKbId(kbId));
            graphService.clearGraph(kbId);

            if (!conversationIds.isEmpty()) {
                chatMessageRepository.deleteByConversationIdIn(conversationIds);
            }
            knowledgeCardRepository.deleteByKbId(kbId);
            for (UUID documentId : documentIds) {
                documentChunkRepository.deleteByDocumentId(documentId);
            }
            if (!documentIds.isEmpty()) {
                documentRepository.deleteAllByIdInBatch(documentIds);
            }
            conversationRepository.deleteByKbId(kbId);
            knowledgeBaseRepository.deleteById(kbId);
        }

        int deletedMinioObjects = 0;
        for (String path : minioPaths) {
            if (minioService.delete(path)) {
                deletedMinioObjects++;
            }
        }
        stats.setMinioObjectsDeleted(deletedMinioObjects);
        return stats;
    }

    public ResetSeedResponse resetAndReseed(KnowledgeBaseResetRequest request) {
        MultipartFile file = request.getFile();
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("测试文档不能为空");
        }

        ResetCleanupStats cleanupStats = hardDeleteAllKnowledgeBases();
        KnowledgeBaseDTO dto = new KnowledgeBaseDTO();
        dto.setName(request.getName());
        dto.setDescription(request.getDescription());
        dto.setIcon(request.getIcon());

        KnowledgeBase knowledgeBase = knowledgeBaseService.create(dto);
        DocumentDTO uploaded = documentService.uploadDocument(knowledgeBase.getId(), file);
        Document readyDocument = waitForDocumentReady(uploaded.getId());
        Conversation conversation = conversationService.createConversation(request.getConversationTitle(), knowledgeBase.getId());

        return ResetSeedResponse.builder()
                .knowledgeBaseId(knowledgeBase.getId())
                .documentId(readyDocument.getId())
                .conversationId(conversation.getId())
                .documentStatus(readyDocument.getStatus())
                .cleanup(cleanupStats)
                .build();
    }

    private int deleteDocumentVectorsByKbId(UUID kbId) {
        return jdbcTemplate.update(
                "DELETE FROM vector_store WHERE metadata->>'kb_id' = ? AND COALESCE(metadata->>'source_type', '') <> 'knowledge_card'",
                kbId.toString());
    }

    private int deleteKnowledgeCardVectorsByKbId(UUID kbId) {
        return jdbcTemplate.update(
                "DELETE FROM vector_store WHERE metadata->>'kb_id' = ? AND metadata->>'source_type' = 'knowledge_card'",
                kbId.toString());
    }

    private long countDocumentChunks(List<UUID> documentIds) {
        if (documentIds.isEmpty()) {
            return 0;
        }
        return documentIds.stream()
                .map(documentChunkRepository::findByDocumentIdOrderByChunkIndexAsc)
                .mapToLong(List::size)
                .sum();
    }

    private Document waitForDocumentReady(UUID documentId) {
        Instant deadline = Instant.now().plus(DOCUMENT_READY_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            Document document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new IllegalArgumentException("文档不存在: " + documentId));
            if ("READY".equalsIgnoreCase(document.getStatus())) {
                return document;
            }
            if ("FAILED".equalsIgnoreCase(document.getStatus())) {
                throw new IllegalStateException("重建测试文档失败: " + document.getErrorMessage());
            }
            try {
                Thread.sleep(DOCUMENT_READY_POLL_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待测试文档就绪时被中断", e);
            }
        }
        throw new IllegalStateException("等待测试文档就绪超时: " + documentId);
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class ResetSeedResponse {
        private UUID knowledgeBaseId;
        private UUID documentId;
        private UUID conversationId;
        private String documentStatus;
        private ResetCleanupStats cleanup;
    }

    @Data
    public static class ResetCleanupStats {
        private int knowledgeBasesDeleted;
        private int documentsDeleted;
        private long documentChunksDeleted;
        private int conversationsDeleted;
        private long knowledgeCardsDeleted;
        private int documentVectorsDeleted;
        private int knowledgeCardVectorsDeleted;
        private int minioObjectsDeleted;
    }
}
