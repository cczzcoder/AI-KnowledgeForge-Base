package com.knowledgeforge.system.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    private UUID messageId;
    private UUID conversationId;
    private String answer;
    private List<SourceDTO> sources;
    private int totalTokens;
    private long latencyMs;
    private CredibilityBreakdownDTO credibility;
}