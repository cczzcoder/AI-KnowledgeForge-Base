package com.knowledgeforge.system.conversation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDTO {

    private UUID id;
    private String title;
    private UUID kbId;
    private int messageCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String cleanupStatus;
    private LocalDateTime expiresAt;
    private LocalDateTime remindAt;
    private boolean expiringSoon;
    private Long daysUntilExpiry;
    private String retentionNotice;
    private List<ChatMessageDTO> messages;
}
