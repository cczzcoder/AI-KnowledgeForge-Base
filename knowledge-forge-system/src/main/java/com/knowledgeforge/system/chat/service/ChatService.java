package com.knowledgeforge.system.chat.service;

import com.knowledgeforge.core.entity.ChatMessage;
import com.knowledgeforge.core.entity.Conversation;
import com.knowledgeforge.system.chat.dto.ChatRequest;
import com.knowledgeforge.system.chat.dto.ChatResponse;
import com.knowledgeforge.system.chat.dto.CredibilityBreakdownDTO;
import com.knowledgeforge.system.chat.dto.SourceDTO;
import com.knowledgeforge.system.conversation.repository.ChatMessageRepository;
import com.knowledgeforge.system.conversation.repository.ConversationRepository;
import com.knowledgeforge.system.knowledge.repository.DocumentRepository;
import com.knowledgeforge.system.retrieval.service.HybridSearchService;
import com.knowledgeforge.system.knowledgecard.service.KnowledgeExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatClient chatClient;
    private final HybridSearchService hybridSearchService;
    private final DocumentRepository documentRepository;
    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final CredibilityService credibilityService;
    private final KnowledgeExtractionService knowledgeExtractionService;

    private static final String SIMPLE_SYSTEM_PROMPT = """
            你的名字叫"知否"，是一个热情友好、知识渊博的AI助手。
            你善于倾听、乐于助人，会根据用户的需求提供专业、准确、温暖的帮助。
            无论是解答问题、协助写作、分析文档，还是聊天陪伴，你都会全力以赴。
            请用中文回答。
            """;

    private static final String RAG_SYSTEM_PROMPT = """
            你的名字叫"知否"，是一个个人知识库助手。请根据以下知识库内容回答用户的问题。

            要求：
            1. 只根据提供的知识内容回答，不要编造信息
            2. 如果知识库中没有相关信息，请明确告知用户
            3. 回答要准确、简洁、有条理
            4. 使用中文回答

            知识库内容：
            %s
            """;

    private static final int AUTO_EXTRACT_MAX_CARDS = 3;

    public ChatResponse chat(ChatRequest request) {
        long startTime = System.currentTimeMillis();

        UUID conversationId = request.getConversationId();
        if (conversationId == null) {
            conversationId = createNewConversation(request.getMessage(), request.getKbId());
        }

        saveUserMessage(conversationId, request.getMessage());

        List<Document> retrievedDocs = retrieveDocuments(request);
        // 一次查询解析文档标题，供 buildContext 和 buildSources 共用（消除重复查询）
        Map<UUID, String> docTitleMap = resolveDocumentTitles(retrievedDocs);

        String context = buildContext(retrievedDocs, docTitleMap);
        ChatResponse response;

        if (context.isBlank()) {
            String answer = "抱歉，知识库中暂未找到与该问题相关的信息。\n\n您可以尝试：\n1. 上传相关文档到知识库\n2. 换个方式描述您的问题\n3. 指定已有的知识库ID进行检索";
            UUID messageId = saveAssistantMessage(conversationId, answer);
            response = ChatResponse.builder()
                    .messageId(messageId)
                    .conversationId(conversationId)
                    .answer(answer)
                    .sources(List.of())
                    .totalTokens(0)
                    .latencyMs(System.currentTimeMillis() - startTime)
                    .build();
        } else {
            try {
                String systemPrompt = String.format(RAG_SYSTEM_PROMPT, context);

                var aiResponse = chatClient.prompt(new Prompt(List.of(
                        new SystemMessage(systemPrompt),
                        new UserMessage(request.getMessage())
                ))).call().chatResponse();

                String answerText = aiResponse != null
                        && aiResponse.getResult() != null
                        && aiResponse.getResult().getOutput() != null
                        ? aiResponse.getResult().getOutput().getText()
                        : "抱歉，生成回答时出现错误";

                List<SourceDTO> sources = buildSources(retrievedDocs, docTitleMap);

                CredibilityBreakdownDTO credibility = credibilityService.calculateCredibility(
                        retrievedDocs, request.getKbId());

                UUID messageId = saveAssistantMessage(conversationId, answerText);
                response = ChatResponse.builder()
                        .messageId(messageId)
                        .conversationId(conversationId)
                        .answer(answerText)
                        .sources(sources)
                        .credibility(credibility)
                        .totalTokens(0)
                        .latencyMs(System.currentTimeMillis() - startTime)
                        .build();
            } catch (Exception e) {
                log.error("RAG 对话失败: {}", e.getMessage(), e);
                String errorAnswer = "抱歉，生成回答时出现错误，请稍后重试。";
                UUID messageId = saveAssistantMessage(conversationId, errorAnswer);
                response = ChatResponse.builder()
                        .messageId(messageId)
                        .conversationId(conversationId)
                        .answer(errorAnswer)
                        .sources(List.of())
                        .totalTokens(0)
                        .latencyMs(System.currentTimeMillis() - startTime)
                        .build();
            }
        }

        updateConversationTime(conversationId);

        // 异步触发知识提取（非阻塞）
        triggerKnowledgeExtraction(request, conversationId);

        log.info("RAG 对话完成: 召回 {} 条, 耗时 {}ms", retrievedDocs.size(), System.currentTimeMillis() - startTime);
        return response;
    }

    public ChatResponse simpleChat(ChatRequest request) {
        long startTime = System.currentTimeMillis();
        UUID conversationId = request.getConversationId();
        if (conversationId == null) {
            conversationId = createNewConversation(request.getMessage(), request.getKbId());
        }
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
            return ChatResponse.builder()
                    .messageId(messageId)
                    .conversationId(conversationId)
                    .answer(answerText)
                    .sources(List.of())
                    .totalTokens(0)
                    .latencyMs(System.currentTimeMillis() - startTime)
                    .build();
        } catch (Exception e) {
            log.error("普通对话失败: {}", e.getMessage(), e);
            String errorAnswer = "抱歉，生成回答时出现错误，请稍后重试。";
            UUID messageId = saveAssistantMessage(conversationId, errorAnswer);
            return ChatResponse.builder()
                    .messageId(messageId)
                    .conversationId(conversationId)
                    .answer(errorAnswer)
                    .sources(List.of())
                    .totalTokens(0)
                    .latencyMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    public Flux<ServerSentEvent<String>> chatStream(ChatRequest request) {
        final UUID conversationId = request.getConversationId() != null
                ? request.getConversationId()
                : createNewConversation(request.getMessage(), request.getKbId());
        saveUserMessage(conversationId, request.getMessage());
        List<Document> retrievedDocs = retrieveDocuments(request);
        // 一次查询解析文档标题，供 buildContext 和 buildSources 共用（消除重复查询）
        Map<UUID, String> docTitleMap = resolveDocumentTitles(retrievedDocs);
        String context = buildContext(retrievedDocs, docTitleMap);
        List<org.springframework.ai.chat.messages.Message> messages = new java.util.ArrayList<>();
        if (!context.isBlank()) {
            messages.add(new SystemMessage(String.format(RAG_SYSTEM_PROMPT, context)));
        }
        messages.add(new UserMessage(request.getMessage()));
        AtomicReference<String> fullResponse = new AtomicReference<>("");

        List<SourceDTO> sources = buildSources(retrievedDocs, docTitleMap);

        try {
            CredibilityBreakdownDTO credibility = credibilityService.calculateCredibility(
                    retrievedDocs, request.getKbId());
            String sourcesJson = new ObjectMapper().writeValueAsString(sources);
            String credibilityJson = new ObjectMapper().writeValueAsString(credibility);
            String metadataJson = String.format(
                    "{\"conversationId\":\"%s\",\"sources\":%s,\"credibility\":%s}",
                    conversationId, sourcesJson, credibilityJson);

            return chatClient.prompt(new Prompt(messages))
                    .stream()
                    .chatResponse()
                    .map(chunk -> {
                        String text = chunk.getResult() != null && chunk.getResult().getOutput() != null
                                ? chunk.getResult().getOutput().getText()
                                : "";
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
                        saveAssistantMessage(conversationId, fullResponse.get());
                        updateConversationTime(conversationId);
                        triggerKnowledgeExtraction(request, conversationId);
                    }).then(Mono.empty()))
                    .doOnComplete(() -> log.info("流式对话完成: conversationId={}", conversationId))
                    .doOnError(error -> {
                        log.error("流式对话出错", error);
                        saveAssistantMessage(conversationId, "流式响应出错，请稍后重试。");
                    });
        } catch (Exception e) {
            log.error("流式对话启动失败: {}", e.getMessage(), e);
            saveAssistantMessage(conversationId, "流式响应出错，请稍后重试。");
            return Flux.error(e);
        }
    }

    public List<ChatMessage> getConversationMessages(UUID conversationId) {
        return chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    private List<Document> retrieveDocuments(ChatRequest request) {
        if (request.getKbIds() != null && !request.getKbIds().isEmpty()) {
            return hybridSearchService.hybridSearchMultipleKbs(request.getMessage(), request.getKbIds());
        } else if (request.getKbId() != null) {
            return hybridSearchService.hybridSearch(request.getMessage(), request.getKbId());
        }
        return List.of();
    }

    private List<SourceDTO> buildSources(List<Document> retrievedDocs, Map<UUID, String> docTitleMap) {
        return retrievedDocs.stream()
                .filter(doc -> doc.getMetadata().get("document_id") != null)
                .map(doc -> {
                    UUID docId = UUID.fromString(Objects.toString(doc.getMetadata().get("document_id")));
                    Object chunkIdObj = doc.getMetadata().get("chunk_id");
                    UUID chunkId = chunkIdObj != null ? UUID.fromString(chunkIdObj.toString()) : UUID.randomUUID();
                    String content = doc.getText();
                    String snippet = content.length() > 150 ? content.substring(0, 150) + "..." : content;
                    double score = ((Number) doc.getMetadata().getOrDefault("hybrid_score",
                            doc.getMetadata().getOrDefault("distance", 0.0))).doubleValue();

                    return SourceDTO.builder()
                            .chunkId(chunkId)
                            .documentId(docId)
                            .documentTitle(docTitleMap.getOrDefault(docId, "未知文档"))
                            .snippet(snippet)
                            .similarityScore(Math.round(score * 10000.0) / 10000.0)
                            .build();
                }).toList();
    }

    private String buildContext(List<Document> docs, Map<UUID, String> docTitleMap) {
        if (docs.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        int sourceIndex = 0;
        for (Document doc : docs) {
            String docIdStr = Objects.toString(doc.getMetadata().get("document_id"), "");
            if (docIdStr.isBlank()) {
                continue;
            }
            UUID docId;
            try {
                docId = UUID.fromString(docIdStr);
            } catch (IllegalArgumentException e) {
                continue;
            }
            String title = docTitleMap.getOrDefault(docId, "未知文档");
            sourceIndex++;
            sb.append(String.format("【来源 %d：%s】\n", sourceIndex, title));
            sb.append(doc.getText());
            sb.append("\n\n");
        }
        return sb.toString();
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
                        com.knowledgeforge.core.entity.Document::getId,
                        com.knowledgeforge.core.entity.Document::getTitle
                ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID createNewConversation(String firstMessage) {
        return createNewConversation(firstMessage, null);
    }

    /**
     * 创建对话（可选关联知识库）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID createNewConversation(String firstMessage, UUID kbId) {
        String title = firstMessage.length() > 30
                ? firstMessage.substring(0, 30) + "..."
                : firstMessage;
        Conversation conv = Conversation.builder()
                .title(title)
                .kbId(kbId)
                .build();
        return conversationRepository.save(conv).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveUserMessage(UUID conversationId, String content) {
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
        conversationRepository.findById(conversationId).ifPresent(conv -> {
            conv.setUpdatedAt(LocalDateTime.now());
            conversationRepository.save(conv);
        });
    }

    /**
     * 对话完成后异步触发知识提取。
     * 使用 CompletableFuture 确保不阻塞主流程响应。
     */
    private void triggerKnowledgeExtraction(ChatRequest request, UUID conversationId) {
        UUID kbId = request.getKbId();
        if (request.getKbIds() != null && !request.getKbIds().isEmpty()) {
            kbId = request.getKbIds().get(0);
        }
        if (kbId == null) {
            return; // 没有知识库上下文，跳过提取
        }
        final UUID finalKbId = kbId;
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                knowledgeExtractionService.extractFromConversation(finalKbId, conversationId, AUTO_EXTRACT_MAX_CARDS);
                log.info("自动知识提取触发成功: conversationId={}, kbId={}", conversationId, finalKbId);
            } catch (Exception e) {
                log.warn("自动知识提取失败: conversationId={}, error={}", conversationId, e.getMessage());
            }
        });
    }
}