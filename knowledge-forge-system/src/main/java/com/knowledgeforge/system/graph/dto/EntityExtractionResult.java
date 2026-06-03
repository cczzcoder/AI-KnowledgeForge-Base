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
public class EntityExtractionResult {
    private List<ExtractedEntity> entities;
    private List<ExtractedRelation> relations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtractedEntity {
        private String name;
        private String entityType;
        private String description;
        private List<String> aliases;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtractedRelation {
        private String sourceEntity;
        private String targetEntity;
        private String relationType;
        private String description;
    }
}