package com.knowledgeforge.system.chat.memory;

import com.knowledgeforge.core.entity.ChatMessage;
import com.knowledgeforge.core.shared.constant.SystemConstants;
import com.knowledgeforge.system.conversation.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DatabaseChatMemory implements ChatMemory {

    private final ChatMessageRepository chatMessageRepository;

    @Override
    public List<Message> get(String conversationId) {
        UUID convId = parseConversationId(conversationId);
        List<ChatMessage> messages = chatMessageRepository
                .findByConversationIdOrderByCreatedAtAsc(convId);

        int lastN = SystemConstants.MAX_HISTORY_MESSAGES;
        int start = Math.max(0, messages.size() - lastN);
        List<Message> result = new ArrayList<>();
        for (int i = start; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            result.add(toAiMessage(msg));
        }
        return result;
    }

    @Override
    @Transactional
    public void add(String conversationId, List<Message> messages) {
        UUID convId = parseConversationId(conversationId);
        for (Message msg : messages) {
            ChatMessage entity = ChatMessage.builder()
                    .conversationId(convId)
                    .role(msg.getMessageType().name())
                    .content(msg.getText())
                    .metadata("{}")
                    .build();
            chatMessageRepository.save(entity);
        }
    }

    @Override
    @Transactional
    public void clear(String conversationId) {
        chatMessageRepository.deleteByConversationId(parseConversationId(conversationId));
    }

    private static UUID parseConversationId(String conversationId) {
        try {
            return UUID.fromString(conversationId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的对话ID格式: " + conversationId, e);
        }
    }

    private Message toAiMessage(ChatMessage msg) {
        return new Message() {
            @Override
            public MessageType getMessageType() {
                try {
                    return MessageType.valueOf(msg.getRole());
                } catch (IllegalArgumentException e) {
                    return MessageType.USER;
                }
            }

            @Override
            public String getText() {
                return msg.getContent();
            }

            @Override
            public Map<String, Object> getMetadata() {
                return Collections.emptyMap();
            }
        };
    }
}