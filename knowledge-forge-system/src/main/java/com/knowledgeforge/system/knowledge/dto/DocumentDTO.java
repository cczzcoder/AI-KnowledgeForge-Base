package com.knowledgeforge.system.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentDTO {

    private UUID id;
    private UUID kbId;
    private String title;
    private String fileType;
    private Long fileSize;
    private Integer chunkCount;
    private String status;
    private String errorMessage;
    private LocalDateTime createdAt;
}