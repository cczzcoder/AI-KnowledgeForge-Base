package com.knowledgeforge.system.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceDTO {

    private UUID chunkId;
    private UUID documentId;
    private String documentTitle;
    private String snippet;
    private double similarityScore;
}