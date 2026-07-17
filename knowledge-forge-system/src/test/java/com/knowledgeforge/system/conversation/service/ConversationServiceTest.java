package com.knowledgeforge.system.conversation.service;

import com.knowledgeforge.core.entity.ChatMessage;
import com.knowledgeforge.core.entity.Conversation;
import com.knowledgeforge.system.conversation.dto.ConversationCreateDTO;
import com.knowledgeforge.system.conversation.dto.ConversationDTO;
import com.knowledgeforge.system.conversation.repository.ChatMessageRepository;
import com.knowledgeforge.system.conversation.repository.ConversationRepository;
import com.knowledgeforge.system.knowledgecard.service.KnowledgeExtractionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private KnowledgeExtractionService knowledgeExtractionService;

    private ConversationService conversationService;

    @BeforeEach
    void setUp() {
        conversationService = new ConversationService(
                conversationRepository,
                chatMessageRepository,
                knowledgeExtractionService
        );
    }

    @Test
    void create_initializesRetentionWindow() {
        ConversationCreateDTO dto = new ConversationCreateDTO();
        dto.setTitle("测试会话");
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ConversationDTO conversation = conversationService.create(dto);

        assertThat(conversation.getCleanupStatus()).isEqualTo("ACTIVE");
        assertThat(conversation.getExpiresAt()).isNotNull();
        assertThat(conversation.getRemindAt()).isNotNull();
        assertThat(conversation.isExpiringSoon()).isFalse();
    }

    @Test
    void getActiveConversation_rejectsExpiredConversation() {
        UUID conversationId = UUID.randomUUID();
        Conversation expired = Conversation.builder()
                .id(conversationId)
                .title("旧会话")
                .updatedAt(java.time.LocalDateTime.now().minusDays(31))
                .deleted(false)
                .build();
        when(conversationRepository.findByIdAndDeletedFalse(conversationId)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> conversationService.getActiveConversation(conversationId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("对话已过期");
    }

    @Test
    void applyRetentionPolicies_marksExpiringConversation() {
        Conversation expiring = Conversation.builder()
                .id(UUID.randomUUID())
                .title("快过期")
                .updatedAt(java.time.LocalDateTime.now().minusDays(24))
                .deleted(false)
                .build();
        when(conversationRepository.findByDeletedFalseAndUpdatedAtBefore(any())).thenReturn(List.of(expiring));

        int affected = conversationService.applyRetentionPolicies();

        assertThat(affected).isEqualTo(1);
        assertThat(expiring.getCleanupStatus()).isEqualTo("EXPIRING");
        assertThat(expiring.getRemindedAt()).isNotNull();
    }

    @Test
    void purgeExpiredConversations_deletesMessagesAndConversation() {
        Conversation expired = Conversation.builder()
                .id(UUID.randomUUID())
                .title("已过期")
                .updatedAt(java.time.LocalDateTime.now().minusDays(31))
                .deleted(false)
                .build();
        when(conversationRepository.findByDeletedFalseAndUpdatedAtBefore(any())).thenReturn(List.of(expired));

        int purged = conversationService.purgeExpiredConversations();

        assertThat(purged).isEqualTo(1);
        verify(chatMessageRepository).deleteByConversationId(expired.getId());
        verify(conversationRepository).delete(expired);
    }

    @Test
    void getMessages_blocksExpiredConversation() {
        UUID conversationId = UUID.randomUUID();
        Conversation expired = Conversation.builder()
                .id(conversationId)
                .title("已过期")
                .updatedAt(java.time.LocalDateTime.now().minusDays(31))
                .deleted(false)
                .build();
        when(conversationRepository.findByIdAndDeletedFalse(conversationId)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> conversationService.getMessages(conversationId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("对话已过期");

        verify(chatMessageRepository, never()).findByConversationIdOrderByCreatedAtAsc(any());
    }
}
