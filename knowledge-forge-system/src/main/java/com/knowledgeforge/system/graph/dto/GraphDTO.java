package com.knowledgeforge.system.graph.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphDTO {
    private List<GraphNodeDTO> nodes;
    private List<GraphEdgeDTO> edges;
    private int nodeCount;
    private int edgeCount;
}