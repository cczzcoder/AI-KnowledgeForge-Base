package com.knowledgeforge.system.chat.controller;

import com.knowledgeforge.core.entity.ChatMessage;
import com.knowledgeforge.core.shared.constant.SystemConstants;
import com.knowledgeforge.core.shared.dto.ApiResponse;
import com.knowledgeforge.system.chat.dto.ChatRequest;
import com.knowledgeforge.system.chat.dto.ChatResponse;
import com.knowledgeforge.system.chat.service.ChatService;
import com.knowledgeforge.system.conversation.repository.ChatMessageRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(SystemConstants.API_V1 + "/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ChatMessageRepository chatMessageRepository;

    @PostMapping("/rag")
    public ApiResponse<ChatResponse> ragChat(@Valid @RequestBody ChatRequest request) {
        return ApiResponse.success(chatService.chat(request));
    }

    @PostMapping(value = "/rag/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> ragChatStream(@Valid @RequestBody ChatRequest request) {
        return chatService.chatStream(request);
    }

    @PostMapping("/simple")
    public ApiResponse<ChatResponse> simpleChat(@Valid @RequestBody ChatRequest request) {
        return ApiResponse.success(chatService.simpleChat(request));
    }

    @PatchMapping("/messages/{id}/feedback")
    public ApiResponse<Void> submitFeedback(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        String feedback = body.get("feedback");
        ChatMessage message = chatMessageRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("消息不存在: " + id));
        message.setFeedback(feedback);
        chatMessageRepository.save(message);
        return ApiResponse.success(null);
    }
}