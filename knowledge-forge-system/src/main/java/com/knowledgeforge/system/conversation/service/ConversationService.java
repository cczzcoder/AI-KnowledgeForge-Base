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

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    public static final int RETENTION_DAYS = 30;
    public static final int REMIND_DAYS_BEFORE = 7;

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final KnowledgeExtractionService knowledgeExtractionService;
    private final Clock clock = Clock.systemDefaultZone();

    @Transactional
    public ConversationDTO create(ConversationCreateDTO dto) {
        Conversation conv = createConversation(dto.getTitle(), dto.getKbId());
        return toDTO(conv, List.of(), now());
    }

    @Transactional
    public Conversation createConversation(String title, UUID kbId) {
        Conversation conv = Conversation.builder()
                .title(title)
                .kbId(kbId)
                .build();
        conv = applyRetentionWindow(conv, now());
        return conversationRepository.save(conv);
    }

    public PageResult<ConversationDTO> list(int page, int size) {
        var result = conversationRepository.findByDeletedFalseOrderByUpdatedAtDesc(PageRequest.of(page, size));
        List<Conversation> conversations = result.getContent();
        List<UUID> conversationIds = conversations.stream().map(Conversation::getId).toList();
        Map<UUID, List<ChatMessage>> messagesMap = chatMessageRepository
                .findByConversationIdInOrderByCreatedAtAsc(conversationIds)
                .stream()
                .collect(Collectors.groupingBy(ChatMessage::getConversationId));
        LocalDateTime now = now();
        List<ConversationDTO> items = conversations.stream()
                .map(conv -> toDTO(conv, messagesMap.getOrDefault(conv.getId(), List.of()), now))
                .collect(Collectors.toList());
        return PageResult.of(items, result.getTotalElements(), page, size);
    }

    public ConversationDTO getById(UUID id) {
        Conversation conv = getActiveConversation(id);
        List<ChatMessage> messages = chatMessageRepository
                .findByConversationIdOrderByCreatedAtAsc(conv.getId());
        return toDTO(conv, messages, now());
    }

    public Conversation getActiveConversation(UUID id) {
        Conversation conv = conversationRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new IllegalArgumentException("对话不存在或已被清理: " + id));
        refreshRetentionState(conv, now());
        if ("EXPIRED".equals(conv.getCleanupStatus())) {
            throw new IllegalArgumentException("对话已过期，请新建会话继续使用");
        }
        return conv;
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
        getActiveConversation(conversationId);
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
        conv.setUpdatedAt(now());
        conversationRepository.save(conv);
    }

    @Transactional
    public int applyRetentionPolicies() {
        LocalDateTime now = now();
        List<Conversation> staleConversations = conversationRepository.findByDeletedFalseAndUpdatedAtBefore(
                now.minusDays(RETENTION_DAYS - REMIND_DAYS_BEFORE));
        int affected = 0;
        for (Conversation conversation : staleConversations) {
            String previousStatus = cleanupStatusOf(conversation);
            refreshRetentionState(conversation, now);
            if (!cleanupStatusOf(conversation).equals(previousStatus)) {
                affected++;
            }
        }
        return affected;
    }

    @Transactional
    public int purgeExpiredConversations() {
        LocalDateTime now = now();
        List<Conversation> staleConversations = conversationRepository.findByDeletedFalseAndUpdatedAtBefore(
                now.minusDays(RETENTION_DAYS));
        int purged = 0;
        for (Conversation conversation : staleConversations) {
            refreshRetentionState(conversation, now);
            if ("EXPIRED".equals(cleanupStatusOf(conversation))) {
                conversation.setDeleted(true);
                chatMessageRepository.deleteByConversationId(conversation.getId());
                conversationRepository.delete(conversation);
                purged++;
            }
        }
        return purged;
    }

    @Transactional
    public void touchConversation(UUID conversationId) {
        conversationRepository.findById(conversationId).ifPresent(conv -> {
            conv.setUpdatedAt(now());
            applyRetentionWindow(conv, now());
            conversationRepository.save(conv);
        });
    }

    private ConversationDTO toDTO(Conversation conv, List<ChatMessage> messages, LocalDateTime now) {
        refreshRetentionState(conv, now);
        long daysUntilExpiry = conv.getExpiresAt() == null
                ? Long.MAX_VALUE
                : Math.max(0, ChronoUnit.DAYS.between(now.toLocalDate(), conv.getExpiresAt().toLocalDate()));
        boolean expiringSoon = "EXPIRING".equals(cleanupStatusOf(conv));
        return ConversationDTO.builder()
                .id(conv.getId())
                .title(conv.getTitle())
                .kbId(conv.getKbId())
                .messageCount(messages.size())
                .createdAt(conv.getCreatedAt())
                .updatedAt(conv.getUpdatedAt())
                .cleanupStatus(conv.getCleanupStatus())
                .expiresAt(conv.getExpiresAt())
                .remindAt(conv.getRemindAt())
                .expiringSoon(expiringSoon)
                .daysUntilExpiry(conv.getExpiresAt() == null ? null : daysUntilExpiry)
                .retentionNotice(buildRetentionNotice(conv, daysUntilExpiry))
                .messages(messages.stream().map(this::toMessageDTO).collect(Collectors.toList()))
                .build();
    }

    private String buildRetentionNotice(Conversation conversation, long daysUntilExpiry) {
        return switch (cleanupStatusOf(conversation)) {
            case "EXPIRING" -> "该对话将在 " + daysUntilExpiry + " 天后自动清理";
            case "EXPIRED" -> "该对话已过期，需要新建会话继续使用";
            default -> null;
        };
    }

    private Conversation applyRetentionWindow(Conversation conversation, LocalDateTime now) {
        LocalDateTime expiresAt = conversation.getUpdatedAt().plusDays(RETENTION_DAYS);
        conversation.setCleanupStatus(Conversation.CleanupStatus.ACTIVE.name());
        conversation.setExpiresAt(expiresAt);
        conversation.setRemindAt(expiresAt.minusDays(REMIND_DAYS_BEFORE));
        conversation.setRemindedAt(null);
        return conversation;
    }

    private void refreshRetentionState(Conversation conversation, LocalDateTime now) {
        if (conversation.getUpdatedAt() == null) {
            conversation.setUpdatedAt(now);
        }
        if (conversation.getExpiresAt() == null || conversation.getRemindAt() == null) {
            applyRetentionWindow(conversation, now);
        }
        if (!now.isBefore(conversation.getExpiresAt())) {
            conversation.setCleanupStatus(Conversation.CleanupStatus.EXPIRED.name());
            if (conversation.getRemindedAt() == null) {
                conversation.setRemindedAt(now);
            }
            return;
        }
        if (!now.isBefore(conversation.getRemindAt())) {
            conversation.setCleanupStatus(Conversation.CleanupStatus.EXPIRING.name());
            if (conversation.getRemindedAt() == null) {
                conversation.setRemindedAt(now);
            }
            return;
        }
        conversation.setCleanupStatus(Conversation.CleanupStatus.ACTIVE.name());
    }

    private String cleanupStatusOf(Conversation conversation) {
        return conversation.getCleanupStatus() == null ? "ACTIVE" : conversation.getCleanupStatus();
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

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
