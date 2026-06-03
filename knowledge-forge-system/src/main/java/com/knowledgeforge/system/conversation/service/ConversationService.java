package com.knowledgeforge.system.conversation.service;

import com.knowledgeforge.core.entity.ChatMessage;
import com.knowledgeforge.core.entity.Conversation;
import com.knowledgeforge.core.shared.dto.PageResult;
import com.knowledgeforge.system.conversation.dto.ChatMessageDTO;
import com.knowledgeforge.system.conversation.dto.ConversationCreateDTO;
import com.knowledgeforge.system.conversation.dto.ConversationDTO;
import com.knowledgeforge.system.conversation.repository.ChatMessageRepository;
import com.knowledgeforge.system.conversation.repository.ConversationRepository;
import com.knowledgeforge.system.knowledgecard.dto.KnowledgeCardDTO;
import com.knowledgeforge.system.knowledgecard.service.KnowledgeExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final KnowledgeExtractionService knowledgeExtractionService;

    @Transactional
    public ConversationDTO create(ConversationCreateDTO dto) {
        Conversation conv = Conversation.builder()
                .title(dto.getTitle())
                .kbId(dto.getKbId())
                .build();
        conv = conversationRepository.save(conv);
        return toDTO(conv, List.of());
    }

    public PageResult<ConversationDTO> list(int page, int size) {
        var result = conversationRepository.findByDeletedFalseOrderByUpdatedAtDesc(PageRequest.of(page, size));
        List<Conversation> conversations = result.getContent();
        List<UUID> conversationIds = conversations.stream().map(Conversation::getId).toList();
        Map<UUID, List<ChatMessage>> messagesMap = chatMessageRepository
                .findByConversationIdInOrderByCreatedAtAsc(conversationIds)
                .stream()
                .collect(Collectors.groupingBy(ChatMessage::getConversationId));
        List<ConversationDTO> items = conversations.stream()
                .map(conv -> toDTO(conv, messagesMap.getOrDefault(conv.getId(), List.of())))
                .collect(Collectors.toList());
        return PageResult.of(items, result.getTotalElements(), page, size);
    }

    public ConversationDTO getById(UUID id) {
        Conversation conv = conversationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("对话不存在: " + id));
        List<ChatMessage> messages = chatMessageRepository
                .findByConversationIdOrderByCreatedAtAsc(conv.getId());
        return toDTO(conv, messages);
    }

    public ChatMessage addSystemMessage(UUID conversationId, String content) {
        ChatMessage msg = ChatMessage.builder()
                .conversationId(conversationId)
                .role(ChatMessage.Role.SYSTEM.name())
                .content(content)
                .metadata("{}")
                .build();
        return chatMessageRepository.save(msg);
    }

    public List<ChatMessageDTO> getMessages(UUID conversationId) {
        return chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId)
                .stream()
                .map(this::toMessageDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public void delete(UUID id) {
        Conversation conv = conversationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("对话不存在: " + id));
        conv.setDeleted(true);
        conv.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conv);
    }

    private ConversationDTO toDTO(Conversation conv, List<ChatMessage> messages) {
        return ConversationDTO.builder()
                .id(conv.getId())
                .title(conv.getTitle())
                .kbId(conv.getKbId())
                .messageCount(messages.size())
                .createdAt(conv.getCreatedAt())
                .updatedAt(conv.getUpdatedAt())
                .messages(messages.stream().map(this::toMessageDTO).collect(Collectors.toList()))
                .build();
    }

    /**
     * 异步触发知识提取（非阻塞，不干扰对话主流程）。
     * 在对话完成后调用，由后台线程执行 LLM 分析并生成待审核知识卡片。
     *
     * @param kbId          知识库ID
     * @param conversationId 会话ID
     * @param maxCards      最多提取卡片数
     */
    public void triggerKnowledgeExtractionAsync(UUID kbId, UUID conversationId, int maxCards) {
        CompletableFuture.runAsync(() -> {
            try {
                KnowledgeCardDTO.ExtractionResult result =
                        knowledgeExtractionService.extractFromConversation(kbId, conversationId, maxCards);
                log.info("异步知识提取完成: conversationId={}, 提取{}个知识点, 摘要={}",
                        conversationId,
                        result.getCards() != null ? result.getCards().size() : 0,
                        result.getSummary());
            } catch (Exception e) {
                log.warn("异步知识提取失败: conversationId={}, error={}", conversationId, e.getMessage());
            }
        });
    }

    private ChatMessageDTO toMessageDTO(ChatMessage msg) {
        return ChatMessageDTO.builder()
                .id(msg.getId())
                .role(msg.getRole())
                .content(msg.getContent())
                .feedback(msg.getFeedback())
                .createdAt(msg.getCreatedAt())
                .build();
    }
}