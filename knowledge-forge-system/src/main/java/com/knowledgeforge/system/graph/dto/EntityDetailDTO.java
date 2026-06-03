package com.knowledgeforge.system.graph.dto;

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
public class EntityDetailDTO {
    private UUID id;
    private String name;
    private String entityType;
    private String description;
    private String aliases;
    private UUID sourceDocumentId;
    private UUID sourceChunkId;
    private List<RelatedEntityDTO> relatedEntities;
    private List<GraphEdgeDTO> relatedEdges;
    private int relationCount;
}