package com.knowledgeforge.system.document.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkDescriptor {

    private String content;
    private Integer chunkIndex;
    private Integer tokenCount;
    private String chunkType;
    private String sectionTitle;
    private String sectionPath;
    private Integer startOffset;
    private Integer endOffset;
    private Integer parentRef;
    private String strategyVersion;
}
