package com.knowledgeforge.system.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class ChatRequest {

    @NotBlank(message = "问题不能为空")
    private String message;

    private UUID kbId;

    private UUID conversationId;

    private List<UUID> kbIds;
}