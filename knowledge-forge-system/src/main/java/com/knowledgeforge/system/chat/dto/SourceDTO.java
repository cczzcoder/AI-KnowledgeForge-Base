package com.knowledgeforge.system.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceDTO {

    private UUID chunkId;
    private UUID documentId;
    private String documentTitle;
    private String sectionTitle;
    private String sectionPath;
    private Integer chunkIndex;
    private String snippet;
    private double similarityScore;
    private Boolean adjacent;
    private UUID anchorChunkId;
    private Integer expansionOffset;
    private String retrievalRole;
    private Integer evidenceHits;
    private Set<String> matchedSources;
}
