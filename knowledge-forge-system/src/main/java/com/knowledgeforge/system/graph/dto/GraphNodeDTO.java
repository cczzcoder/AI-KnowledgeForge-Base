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
public class GraphNodeDTO {
    private UUID id;
    private String name;
    private String entityType;
    private String description;
    private UUID sourceDocumentId;
    private Integer relationCount;
}