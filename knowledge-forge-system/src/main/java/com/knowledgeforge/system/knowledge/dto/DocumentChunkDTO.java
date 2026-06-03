package com.knowledgeforge.system.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunkDTO {

    private UUID id;
    private UUID documentId;
    private String content;
    private Integer chunkIndex;
    private Integer tokenCount;
    private Boolean embeddingReady;
}