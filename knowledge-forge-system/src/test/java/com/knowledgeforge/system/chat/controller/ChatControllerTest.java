package com.knowledgeforge.system.chat.controller;

import com.knowledgeforge.core.entity.ChatMessage;
import com.knowledgeforge.system.chat.service.ChatService;
import com.knowledgeforge.system.conversation.repository.ChatMessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatService chatService;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @InjectMocks
    private ChatController chatController;

    @Test
    void submitFeedback_normalizesAliasToPositive() {
        UUID messageId = UUID.randomUUID();
        ChatMessage message = ChatMessage.builder().id(messageId).build();
        when(chatMessageRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        chatController.submitFeedback(messageId, Map.of("feedback", " like "));

        verify(chatMessageRepository).save(any(ChatMessage.class));
        assertThat(message.getFeedback()).isEqualTo("POSITIVE");
    }

    @Test
    void submitFeedback_normalizesAliasToNegative() {
        UUID messageId = UUID.randomUUID();
        ChatMessage message = ChatMessage.builder().id(messageId).build();
        when(chatMessageRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        chatController.submitFeedback(messageId, Map.of("feedback", "thumbs_down"));

        verify(chatMessageRepository).save(any(ChatMessage.class));
        assertThat(message.getFeedback()).isEqualTo("NEGATIVE");
    }

    @Test
    void submitFeedback_clearsBlankFeedback() {
        UUID messageId = UUID.randomUUID();
        ChatMessage message = ChatMessage.builder().id(messageId).feedback("POSITIVE").build();
        when(chatMessageRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        chatController.submitFeedback(messageId, Map.of("feedback", "   "));

        verify(chatMessageRepository).save(any(ChatMessage.class));
        assertThat(message.getFeedback()).isNull();
    }

    @Test
    void submitFeedback_rejectsUnsupportedFeedback() {
        UUID messageId = UUID.randomUUID();

        assertThatThrownBy(() -> chatController.submitFeedback(messageId, Map.of("feedback", "maybe")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }
}
