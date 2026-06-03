package com.knowledgeforge.system.graph.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphEdgeDTO {
    private UUID id;
    private UUID sourceId;
    private UUID targetId;
    private String relationType;
    private String description;
}