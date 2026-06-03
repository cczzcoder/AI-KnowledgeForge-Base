package com.knowledgeforge.system.conversation.controller;

import com.knowledgeforge.core.shared.constant.SystemConstants;
import com.knowledgeforge.core.shared.dto.ApiResponse;
import com.knowledgeforge.core.shared.dto.PageResult;
import com.knowledgeforge.system.conversation.dto.ChatMessageDTO;
import com.knowledgeforge.system.conversation.dto.ConversationCreateDTO;
import com.knowledgeforge.system.conversation.dto.ConversationDTO;
import com.knowledgeforge.system.conversation.service.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(SystemConstants.API_V1 + "/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping
    public ApiResponse<ConversationDTO> create(@Valid @RequestBody ConversationCreateDTO dto) {
        return ApiResponse.success(conversationService.create(dto));
    }

    @GetMapping
    public ApiResponse<PageResult<ConversationDTO>> list(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        int cappedSize = Math.min(size, SystemConstants.MAX_PAGE_SIZE);
        return ApiResponse.success(conversationService.list(page, cappedSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<ConversationDTO> getById(@PathVariable UUID id) {
        return ApiResponse.success(conversationService.getById(id));
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<List<ChatMessageDTO>> getMessages(@PathVariable UUID id) {
        return ApiResponse.success(conversationService.getMessages(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        conversationService.delete(id);
        return ApiResponse.success(null);
    }
}