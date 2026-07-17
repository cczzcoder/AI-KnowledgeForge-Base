package com.knowledgeforge.system.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgeforge.core.entity.ChatMessage;
import com.knowledgeforge.system.chat.dto.ChatRequest;
import com.knowledgeforge.system.chat.dto.ChatResponse;
import com.knowledgeforge.system.chat.dto.CredibilityBreakdownDTO;
import com.knowledgeforge.system.chat.dto.SourceDTO;
import com.knowledgeforge.system.chat.observability.RagMetricsAggregator;
import com.knowledgeforge.system.chat.observability.RagObservation;
import com.knowledgeforge.system.config.ChatContextProperties;
import com.knowledgeforge.system.config.ChatExtractionProperties;
import com.knowledgeforge.system.conversation.dto.ConversationDTO;
import com.knowledgeforge.system.conversation.repository.ChatMessageRepository;
import com.knowledgeforge.system.conversation.service.ConversationService;
import com.knowledgeforge.system.knowledge.repository.DocumentRepository;
import com.knowledgeforge.system.knowledgecard.service.KnowledgeExtractionService;
import com.knowledgeforge.system.retrieval.service.HybridSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatContextProperties chatContextProperties;
    private final ChatExtractionProperties chatExtractionProperties;
    private final ChatClient chatClient;
    private final HybridSearchService hybridSearchService;
    private final DocumentRepository documentRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final CredibilityService credibilityService;
    private final KnowledgeExtractionService knowledgeExtractionService;
    private final ConversationService conversationService;
    private final RagMetricsAggregator ragMetricsAggregator;

    private static final String SIMPLE_SYSTEM_PROMPT = """
            你的名字叫\"知否\"，是一个热情友好、知识渊博的AI助手。
            你善于倾听、乐于助人，会根据用户的需求提供专业、准确、温暖的帮助。
            无论是解答问题、协助写作、分析文档，还是聊天陪伴，你都会全力以赴。
            请用中文回答。
            """;

    private static final String RAG_SYSTEM_PROMPT = """
            你的名字叫\"知否\"，是一个个人知识库助手。请根据以下知识库内容回答用户的问题。

            要求：
            1. 只根据提供的知识内容回答，不要编造信息
            2. 如果知识库中没有相关信息，请明确告知用户
            3. 回答要准确、简洁、有条理
            4. 使用中文回答

            知识库内容：
            %s
            """;

    public ChatResponse chat(ChatRequest request) {
        RagObservation observation = newObservation(RagObservation.Mode.SYNC, request);
        UUID conversationId = observation.timeStage("conversation.resolve", () -> resolveConversationId(request));
        observation.setConversationId(conversationId);
        observation.timeStage("message.user.save", () -> saveUserMessage(conversationId, request.getMessage()));

        List<Document> retrievedDocs = retrieveDocuments(request, observation);
        observation.setRetrievedCount(retrievedDocs.size());
        Map<UUID, String> docTitleMap = observation.timeStage("document_titles.resolve",
                () -> resolveDocumentTitles(retrievedDocs));
        String context = observation.timeStage("context.build", () -> buildContext(retrievedDocs, docTitleMap));
        ChatResponse response;

        if (context.isBlank()) {
            String answer = "抱歉，知识库中暂未找到与该问题相关的信息。\n\n您可以尝试：\n1. 上传相关文档到知识库\n2. 换个方式描述您的问题\n3. 指定已有的知识库ID进行检索";
            observation.setAnswerLength(answer.length());
            UUID messageId = observation.timeStage("message.assistant.save",
                    () -> saveAssistantMessage(conversationId, answer));
            response = withRetention(ChatResponse.builder()
                    .messageId(messageId)
                    .conversationId(conversationId)
                    .answer(answer)
                    .sources(List.of())
                    .totalTokens(0)
                    .build(), conversationId);
        } else {
            try {
                String systemPrompt = String.format(RAG_SYSTEM_PROMPT, context);

                var aiResponse = observation.timeStage("llm.call", () -> chatClient.prompt(new Prompt(List.of(
                        new SystemMessage(systemPrompt),
                        new UserMessage(request.getMessage())
                ))).call().chatResponse());

                String answerText = aiResponse != null
                        && aiResponse.getResult() != null
                        && aiResponse.getResult().getOutput() != null
                        ? aiResponse.getResult().getOutput().getText()
                        : "抱歉，生成回答时出现错误";

                List<SourceDTO> sources = observation.timeStage("sources.build",
                        () -> buildSources(retrievedDocs, docTitleMap));
                observation.setSourceCount(sources.size());
                observation.setAnswerLength(answerText.length());
                CredibilityBreakdownDTO credibility = observation.timeStage("credibility.calculate", () ->
                        credibilityService.calculateCredibility(retrievedDocs, request.getKbId()));

                UUID messageId = observation.timeStage("message.assistant.save",
                        () -> saveAssistantMessage(conversationId, answerText));
                response = withRetention(ChatResponse.builder()
                        .messageId(messageId)
                        .conversationId(conversationId)
                        .answer(answerText)
                        .sources(sources)
                        .credibility(credibility)
                        .totalTokens(0)
                        .build(), conversationId);
            } catch (Exception e) {
                log.error("RAG 对话失败: {}", e.getMessage(), e);
                String errorAnswer = "抱歉，生成回答时出现错误，请稍后重试。";
                observation.setAnswerLength(errorAnswer.length());
                UUID messageId = observation.timeStage("message.assistant.save",
                        () -> saveAssistantMessage(conversationId, errorAnswer));
                response = withRetention(ChatResponse.builder()
                        .messageId(messageId)
                        .conversationId(conversationId)
                        .answer(errorAnswer)
                        .sources(List.of())
                        .totalTokens(0)
                        .build(), conversationId);
                observation.finishError(e);
                observation.timeStage("conversation.touch", () -> updateConversationTime(conversationId));
                observation.timeStage("knowledge_extraction.trigger", () -> triggerKnowledgeExtraction(request, conversationId));
                response.setLatencyMs(observation.getFullResponseMs() != null
                        ? observation.getFullResponseMs()
                        : 0L);
                return response;
            }
        }

        observation.timeStage("conversation.touch", () -> updateConversationTime(conversationId));
        observation.timeStage("knowledge_extraction.trigger", () -> triggerKnowledgeExtraction(request, conversationId));
        response.setLatencyMs(observation.finishSuccess());
        return response;
    }

    public ChatResponse simpleChat(ChatRequest request) {
        long startTime = System.currentTimeMillis();
        UUID conversationId = resolveConversationId(request);
        saveUserMessage(conversationId, request.getMessage());

        try {
            var aiResponse = chatClient.prompt(new Prompt(List.of(
                    new SystemMessage(SIMPLE_SYSTEM_PROMPT),
                    new UserMessage(request.getMessage())
            ))).call().chatResponse();
            String answerText = aiResponse != null
                    && aiResponse.getResult() != null
                    && aiResponse.getResult().getOutput() != null
                    ? aiResponse.getResult().getOutput().getText()
                    : "抱歉，生成回答时出现错误";
            UUID messageId = saveAssistantMessage(conversationId, answerText);
            updateConversationTime(conversationId);
            return withRetention(ChatResponse.builder()
                    .messageId(messageId)
                    .conversationId(conversationId)
                    .answer(answerText)
                    .sources(List.of())
                    .totalTokens(0)
                    .latencyMs(System.currentTimeMillis() - startTime)
                    .build(), conversationId);
        } catch (Exception e) {
            log.error("普通对话失败: {}", e.getMessage(), e);
            String errorAnswer = "抱歉，生成回答时出现错误，请稍后重试。";
            UUID messageId = saveAssistantMessage(conversationId, errorAnswer);
            return withRetention(ChatResponse.builder()
                    .messageId(messageId)
                    .conversationId(conversationId)
                    .answer(errorAnswer)
                    .sources(List.of())
                    .totalTokens(0)
                    .latencyMs(System.currentTimeMillis() - startTime)
                    .build(), conversationId);
        }
    }

    public Flux<ServerSentEvent<String>> chatStream(ChatRequest request) {
        RagObservation observation = newObservation(RagObservation.Mode.STREAM, request);
        final UUID conversationId = observation.timeStage("conversation.resolve", () -> resolveConversationId(request));
        observation.setConversationId(conversationId);
        observation.timeStage("message.user.save", () -> saveUserMessage(conversationId, request.getMessage()));
        List<Document> retrievedDocs = retrieveDocuments(request, observation);
        observation.setRetrievedCount(retrievedDocs.size());
        Map<UUID, String> docTitleMap = observation.timeStage("document_titles.resolve",
                () -> resolveDocumentTitles(retrievedDocs));
        String context = observation.timeStage("context.build", () -> buildContext(retrievedDocs, docTitleMap));
        List<org.springframework.ai.chat.messages.Message> messages = new java.util.ArrayList<>();
        if (!context.isBlank()) {
            messages.add(new SystemMessage(String.format(RAG_SYSTEM_PROMPT, context)));
        }
        messages.add(new UserMessage(request.getMessage()));
        AtomicReference<String> fullResponse = new AtomicReference<>("");
        AtomicBoolean firstTokenRecorded = new AtomicBoolean(false);

        List<SourceDTO> sources = observation.timeStage("sources.build",
                () -> buildSources(retrievedDocs, docTitleMap));
        observation.setSourceCount(sources.size());

        try {
            CredibilityBreakdownDTO credibility = observation.timeStage("credibility.calculate", () ->
                    credibilityService.calculateCredibility(retrievedDocs, request.getKbId()));
            ConversationDTO conversationDTO = conversationService.getById(conversationId);
            String metadataJson = observation.timeStage("metadata.serialize",
                    () -> buildMetadataJson(conversationId, sources, credibility, conversationDTO));

            return observation.timeStage("llm.call", () -> chatClient.prompt(new Prompt(messages))
                    .stream()
                    .chatResponse())
                    .map(chunk -> {
                        String text = chunk.getResult() != null && chunk.getResult().getOutput() != null
                                ? chunk.getResult().getOutput().getText()
                                : "";
                        if (!text.isBlank() && firstTokenRecorded.compareAndSet(false, true)) {
                            observation.markFirstToken();
                        }
                        fullResponse.updateAndGet(current -> current + text);
                        return ServerSentEvent.<String>builder()
                                .id(conversationId.toString())
                                .event("message")
                                .data(text)
                                .build();
                    })
                    .concatWith(Mono.just(ServerSentEvent.<String>builder()
                            .id(conversationId.toString())
                            .event("metadata")
                            .data(metadataJson)
                            .build()))
                    .concatWith(Mono.fromRunnable(() -> {
                        observation.setAnswerLength(fullResponse.get().length());
                        observation.timeStage("message.assistant.save",
                                () -> saveAssistantMessage(conversationId, fullResponse.get()));
                        observation.timeStage("conversation.touch", () -> updateConversationTime(conversationId));
                        observation.timeStage("knowledge_extraction.trigger",
                                () -> triggerKnowledgeExtraction(request, conversationId));
                        observation.finishSuccess();
                    }).then(Mono.empty()))
                    .doOnError(error -> {
                        log.error("流式对话出错", error);
                        observation.timeStage("message.assistant.save",
                                () -> saveAssistantMessage(conversationId, "流式响应出错，请稍后重试。"));
                        observation.setAnswerLength("流式响应出错，请稍后重试。".length());
                        observation.finishError(error);
                    });
        } catch (Exception e) {
            log.error("流式对话启动失败: {}", e.getMessage(), e);
            observation.timeStage("message.assistant.save",
                    () -> saveAssistantMessage(conversationId, "流式响应出错，请稍后重试。"));
            observation.setAnswerLength("流式响应出错，请稍后重试。".length());
            observation.finishError(e);
            return Flux.error(e);
        }
    }

    public List<ChatMessage> getConversationMessages(UUID conversationId) {
        conversationService.getActiveConversation(conversationId);
        return chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    private List<Document> retrieveDocuments(ChatRequest request, RagObservation observation) {
        if (request.getKbIds() != null && !request.getKbIds().isEmpty()) {
            return observation == null
                    ? hybridSearchService.hybridSearchMultipleKbs(request.getMessage(), request.getKbIds())
                    : observation.timeStage("retrieval.total",
                    () -> hybridSearchService.hybridSearchMultipleKbs(request.getMessage(), request.getKbIds(), observation));
        } else if (request.getKbId() != null) {
            return observation == null
                    ? hybridSearchService.hybridSearch(request.getMessage(), request.getKbId())
                    : observation.timeStage("retrieval.total",
                    () -> hybridSearchService.hybridSearch(request.getMessage(), request.getKbId(), observation));
        }
        if (observation != null) {
            observation.recordStage("retrieval.total", 0L);
        }
        return List.of();
    }

    private List<SourceDTO> buildSources(List<Document> retrievedDocs, Map<UUID, String> docTitleMap) {
        return retrievedDocs.stream()
                .map(doc -> toSource(doc, docTitleMap))
                .filter(Objects::nonNull)
                .toList();
    }

    private String buildContext(List<Document> docs, Map<UUID, String> docTitleMap) {
        if (docs.isEmpty()) return "";

        List<Document> anchors = docs.stream()
                .filter(doc -> !isAdjacent(doc))
                .toList();
        List<Document> adjacentDocs = docs.stream()
                .filter(this::isAdjacent)
                .toList();

        List<Document> prioritizedAnchors = prioritizeAnchorsForContext(anchors);
        List<Document> highConfidenceAnchors = prioritizedAnchors.stream()
                .filter(this::isHighConfidenceAnchor)
                .toList();
        List<Document> fallbackAnchors = prioritizedAnchors.stream()
                .filter(doc -> !isHighConfidenceAnchor(doc))
                .toList();

        StringBuilder sb = new StringBuilder();
        ContextBudgetState budgetState = new ContextBudgetState();
        appendContextBlocks(sb, highConfidenceAnchors, docTitleMap, budgetState);
        if (budgetState.includedChunks < chatContextProperties.getMaxChunks()) {
            appendContextBlocks(sb, fallbackAnchors, docTitleMap, budgetState);
        }
        if (budgetState.includedChunks < chatContextProperties.getMaxChunks()) {
            appendContextBlocks(sb, adjacentDocs, docTitleMap, budgetState);
        }
        return sb.toString();
    }

    private List<Document> prioritizeAnchorsForContext(List<Document> anchors) {
        return anchors.stream()
                .sorted(java.util.Comparator
                        .comparingInt(this::evidenceHitsForContext).reversed()
                        .thenComparingInt(this::sourceRankForContext)
                        .thenComparingInt(this::originalOrderForContext))
                .toList();
    }

    private boolean isHighConfidenceAnchor(Document doc) {
        ChatContextProperties.HighConfidenceProperties thresholds = chatContextProperties.getHighConfidence();
        int evidenceHits = evidenceHitsForContext(doc);
        if (evidenceHits >= thresholds.getMinEvidenceHits()) {
            return true;
        }

        double hybridScore = hybridScoreForContext(doc);
        double vectorSimilarity = vectorSimilarityForContext(doc);
        double keywordScore = keywordScoreForContext(doc);
        double graphScore = graphScoreForContext(doc);

        if (vectorSimilarity >= thresholds.getVectorSimilarityThreshold()
                && hybridScore >= thresholds.getVectorHybridScoreThreshold()) {
            return true;
        }
        if (keywordScore >= thresholds.getKeywordScoreThreshold()
                && hybridScore >= thresholds.getKeywordHybridScoreThreshold()) {
            return true;
        }
        return graphScore >= thresholds.getGraphScoreThreshold()
                && hybridScore >= thresholds.getGraphHybridScoreThreshold();
    }

    private void appendContextBlocks(StringBuilder sb, List<Document> docs, Map<UUID, String> docTitleMap,
                                     ContextBudgetState budgetState) {
        for (Document doc : docs) {
            if (budgetState.includedChunks >= chatContextProperties.getMaxChunks()) {
                return;
            }

            UUID docId = parseDocumentId(doc);
            if (docId == null) {
                log.warn("RAG context skipped due to invalid document_id: rawDocumentId={}, rawChunkId={}, role={}, adjacent={}, snippet={}",
                        rawMetadataValue(doc, "document_id"),
                        rawMetadataValue(doc, "chunk_id"),
                        rawMetadataValue(doc, "retrieval_role"),
                        isAdjacent(doc),
                        buildSnippet(doc.getText()));
                continue;
            }

            String chunkKey = Objects.toString(doc.getMetadata().get("chunk_id"), docId + ":" + budgetState.fallbackChunkIndex);
            budgetState.fallbackChunkIndex++;
            if (!budgetState.renderedChunks.add(chunkKey)) {
                continue;
            }

            int currentDocumentChunkCount = budgetState.documentChunkCounts.getOrDefault(docId, 0);
            if (currentDocumentChunkCount >= chatContextProperties.getMaxChunksPerDocument()) {
                continue;
            }

            String block = buildContextBlock(doc, docId, docTitleMap, budgetState.sourceIndex + 1);
            if (sb.length() + block.length() > chatContextProperties.getMaxChars()) {
                return;
            }

            budgetState.sourceIndex++;
            budgetState.includedChunks++;
            budgetState.documentChunkCounts.put(docId, currentDocumentChunkCount + 1);
            sb.append(block);
        }
    }

    private String buildContextBlock(Document doc, UUID docId, Map<UUID, String> docTitleMap, int sourceNumber) {
        String title = docTitleMap.getOrDefault(docId, "未知文档");
        String sectionPath = Objects.toString(doc.getMetadata().get("section_path"), "");
        String retrievalRole = Objects.toString(doc.getMetadata().get("retrieval_role"), "");
        boolean adjacent = isAdjacent(doc);
        boolean sameSection = Boolean.TRUE.equals(doc.getMetadata().get("same_section"));
        String roleLabel = adjacent
                ? (sameSection ? "（同节补充片段）" : "（相邻补充片段）")
                : ("anchor".equals(retrievalRole) ? "（主命中片段）" : "");

        return String.format("【来源 %d：%s%s%s】\n%s\n\n", sourceNumber, title,
                sectionPath.isBlank() ? "" : " / " + sectionPath,
                roleLabel,
                doc.getText());
    }

    private UUID parseDocumentId(Document doc) {
        String docIdStr = Objects.toString(doc.getMetadata().get("document_id"), "");
        if (docIdStr.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(docIdStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isAdjacent(Document doc) {
        String retrievalRole = Objects.toString(doc.getMetadata().get("retrieval_role"), "");
        return Boolean.TRUE.equals(doc.getMetadata().get("adjacent"))
                || "adjacent".equals(retrievalRole);
    }

    private int evidenceHitsForContext(Document doc) {
        Object value = doc.getMetadata().get("evidence_hits");
        return value instanceof Number number ? number.intValue() : 0;
    }

    private int sourceRankForContext(Document doc) {
        Object value = doc.getMetadata().get("source_rank");
        return value instanceof Number number ? number.intValue() : Integer.MAX_VALUE;
    }

    private double hybridScoreForContext(Document doc) {
        Object value = doc.getMetadata().get("hybrid_score");
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private double vectorSimilarityForContext(Document doc) {
        Object value = doc.getMetadata().get("vector_similarity");
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private double keywordScoreForContext(Document doc) {
        Object value = doc.getMetadata().get("keyword_score");
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private double graphScoreForContext(Document doc) {
        Object value = doc.getMetadata().get("graph_score");
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private int originalOrderForContext(Document doc) {
        Object value = doc.getMetadata().get("original_order");
        return value instanceof Number number ? number.intValue() : Integer.MAX_VALUE;
    }

    private String buildSnippet(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= chatContextProperties.getSourceSnippetMaxChars()) {
            return normalized;
        }
        int boundary = normalized.lastIndexOf(' ', chatContextProperties.getSourceSnippetMaxChars());
        int cutIndex = boundary > chatContextProperties.getSourceSnippetMaxChars() / 2
                ? boundary
                : chatContextProperties.getSourceSnippetMaxChars();
        return normalized.substring(0, cutIndex).trim() + "...";
    }

    private static final class ContextBudgetState {
        private int sourceIndex;
        private int includedChunks;
        private int fallbackChunkIndex;
        private final java.util.Set<String> renderedChunks = new java.util.LinkedHashSet<>();
        private final java.util.Map<UUID, Integer> documentChunkCounts = new java.util.HashMap<>();
    }

    private SourceDTO toSource(Document doc, Map<UUID, String> docTitleMap) {
        String docIdStr = Objects.toString(doc.getMetadata().get("document_id"), "");
        if (docIdStr.isBlank()) {
            log.warn("RAG source skipped due to blank document_id: rawChunkId={}, role={}, adjacent={}, snippet={}",
                    rawMetadataValue(doc, "chunk_id"),
                    rawMetadataValue(doc, "retrieval_role"),
                    isAdjacent(doc),
                    buildSnippet(doc.getText()));
            return null;
        }

        UUID docId;
        try {
            docId = UUID.fromString(docIdStr);
        } catch (IllegalArgumentException e) {
            log.warn("RAG source skipped due to invalid document_id: rawDocumentId={}, rawChunkId={}, role={}, adjacent={}, snippet={}",
                    docIdStr,
                    rawMetadataValue(doc, "chunk_id"),
                    rawMetadataValue(doc, "retrieval_role"),
                    isAdjacent(doc),
                    buildSnippet(doc.getText()));
            return null;
        }

        UUID chunkId = parseChunkUuid(doc, doc.getMetadata().get("chunk_id"));
        String snippet = buildSnippet(doc.getText());
        double score = ((Number) doc.getMetadata().getOrDefault("hybrid_score",
                doc.getMetadata().getOrDefault("vector_similarity", 0.0))).doubleValue();
        String sectionTitle = Objects.toString(doc.getMetadata().get("section_title"), null);
        String sectionPath = Objects.toString(doc.getMetadata().get("section_path"), null);
        Integer chunkIndex = doc.getMetadata().get("chunk_index") instanceof Number number
                ? number.intValue()
                : null;
        UUID anchorChunkId = parseChunkUuid(doc, doc.getMetadata().get("anchor_chunk_id"));
        Integer expansionOffset = doc.getMetadata().get("expansion_offset") instanceof Number number
                ? number.intValue()
                : null;
        boolean adjacent = Boolean.TRUE.equals(doc.getMetadata().get("adjacent"))
                || "adjacent".equals(doc.getMetadata().get("retrieval_role"));
        String retrievalRole = Objects.toString(doc.getMetadata().get("retrieval_role"), null);
        Integer evidenceHits = doc.getMetadata().get("evidence_hits") instanceof Number number
                ? number.intValue()
                : null;
        @SuppressWarnings("unchecked")
        java.util.Set<String> matchedSources = doc.getMetadata().get("matched_sources") instanceof java.util.Set<?> rawSet
                ? rawSet.stream().map(String::valueOf).collect(Collectors.toCollection(java.util.LinkedHashSet::new))
                : null;

        return SourceDTO.builder()
                .chunkId(chunkId)
                .documentId(docId)
                .documentTitle(docTitleMap.getOrDefault(docId, "未知文档"))
                .sectionTitle(sectionTitle)
                .sectionPath(sectionPath)
                .chunkIndex(chunkIndex)
                .snippet(snippet)
                .similarityScore(Math.round(score * 10000.0) / 10000.0)
                .adjacent(adjacent)
                .anchorChunkId(anchorChunkId)
                .expansionOffset(expansionOffset)
                .retrievalRole(retrievalRole)
                .evidenceHits(evidenceHits)
                .matchedSources(matchedSources)
                .build();
    }

    private UUID parseChunkUuid(Document doc, Object rawChunkId) {
        if (rawChunkId == null) {
            return UUID.randomUUID();
        }
        String chunkIdStr = rawChunkId.toString().trim();
        if (chunkIdStr.isEmpty()) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(chunkIdStr);
        } catch (IllegalArgumentException ignored) {
            if (chunkIdStr.startsWith("card:")) {
                String suffix = chunkIdStr.substring("card:".length());
                try {
                    UUID parsed = UUID.fromString(suffix);
                    log.warn("RAG source normalized legacy card chunk_id: rawChunkId={}, normalizedChunkId={}",
                            chunkIdStr, parsed);
                    return parsed;
                } catch (IllegalArgumentException ignoredAgain) {
                    // 继续走 deterministic fallback
                }
            }
            UUID fallback = UUID.nameUUIDFromBytes(("invalid-chunk:" + chunkIdStr)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            log.warn("RAG source using fallback chunk UUID: rawChunkId={}, fallbackChunkId={}, rawDocumentId={}, role={}",
                    chunkIdStr,
                    fallback,
                    rawMetadataValue(doc, "document_id"),
                    rawMetadataValue(doc, "retrieval_role"));
            return fallback;
        }
    }

    private String rawMetadataValue(Document doc, String key) {
        Object value = doc.getMetadata().get(key);
        return value == null ? "<null>" : value.toString();
    }

    private Map<UUID, String> resolveDocumentTitles(List<Document> docs) {
        List<UUID> docIds = docs.stream()
                .map(d -> d.getMetadata().get("document_id"))
                .filter(Objects::nonNull)
                .map(v -> {
                    try {
                        return UUID.fromString(v.toString());
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (docIds.isEmpty()) return Map.of();
        return documentRepository.findAllById(docIds).stream()
                .collect(Collectors.toMap(
                        document -> document.getId(),
                        document -> document.getTitle()
                ));
    }

    private RagObservation newObservation(RagObservation.Mode mode, ChatRequest request) {
        RagObservation observation = new RagObservation(ragMetricsAggregator, mode);
        observation.setKbMode(resolveKbMode(request));
        return observation;
    }

    private String resolveKbMode(ChatRequest request) {
        if (request.getKbIds() != null && !request.getKbIds().isEmpty()) {
            return "multi";
        }
        return request.getKbId() != null ? "single" : "none";
    }

    private String buildMetadataJson(UUID conversationId, List<SourceDTO> sources,
                                     CredibilityBreakdownDTO credibility, ConversationDTO conversationDTO) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String sourcesJson = objectMapper.writeValueAsString(sources);
            String credibilityJson = objectMapper.writeValueAsString(credibility);
            String retentionNoticeJson = conversationDTO.getRetentionNotice() == null
                    ? "null"
                    : objectMapper.writeValueAsString(conversationDTO.getRetentionNotice());
            return String.format(
                    "{\"conversationId\":\"%s\",\"sources\":%s,\"credibility\":%s,\"cleanupStatus\":\"%s\",\"expiresAt\":\"%s\",\"retentionNotice\":%s}",
                    conversationId,
                    sourcesJson,
                    credibilityJson,
                    conversationDTO.getCleanupStatus(),
                    conversationDTO.getExpiresAt(),
                    retentionNoticeJson);
        } catch (Exception e) {
            throw new IllegalStateException("构建流式元数据失败", e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID createNewConversation(String firstMessage) {
        return createNewConversation(firstMessage, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID createNewConversation(String firstMessage, UUID kbId) {
        String title = firstMessage.length() > 30
                ? firstMessage.substring(0, 30) + "..."
                : firstMessage;
        return conversationService.createConversation(title, kbId).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveUserMessage(UUID conversationId, String content) {
        conversationService.getActiveConversation(conversationId);
        ChatMessage msg = ChatMessage.builder()
                .conversationId(conversationId)
                .role(ChatMessage.Role.USER.name())
                .content(content)
                .metadata("{}")
                .build();
        chatMessageRepository.save(msg);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID saveAssistantMessage(UUID conversationId, String content) {
        conversationService.getActiveConversation(conversationId);
        ChatMessage msg = ChatMessage.builder()
                .conversationId(conversationId)
                .role(ChatMessage.Role.ASSISTANT.name())
                .content(content)
                .metadata("{}")
                .build();
        return chatMessageRepository.save(msg).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateConversationTime(UUID conversationId) {
        conversationService.touchConversation(conversationId);
    }

    private void triggerKnowledgeExtraction(ChatRequest request, UUID conversationId) {
        UUID kbId = request.getKbId();
        if (request.getKbIds() != null && !request.getKbIds().isEmpty()) {
            kbId = request.getKbIds().get(0);
        }
        if (kbId == null) {
            return;
        }
        final UUID finalKbId = kbId;
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                knowledgeExtractionService.extractFromConversation(
                        finalKbId,
                        conversationId,
                        chatExtractionProperties.getAutoExtractMaxCards());
                log.info("自动知识提取触发成功: conversationId={}, kbId={}", conversationId, finalKbId);
            } catch (Exception e) {
                log.warn("自动知识提取失败: conversationId={}, error={}", conversationId, e.getMessage());
            }
        });
    }

    private UUID resolveConversationId(ChatRequest request) {
        if (request.getConversationId() == null) {
            return createNewConversation(request.getMessage(), request.getKbId());
        }
        conversationService.getActiveConversation(request.getConversationId());
        return request.getConversationId();
    }

    private ChatResponse withRetention(ChatResponse response, UUID conversationId) {
        ConversationDTO conversationDTO = conversationService.getById(conversationId);
        response.setConversationCleanupStatus(conversationDTO.getCleanupStatus());
        response.setConversationExpiresAt(conversationDTO.getExpiresAt());
        response.setRetentionNotice(conversationDTO.getRetentionNotice());
        return response;
    }
}
