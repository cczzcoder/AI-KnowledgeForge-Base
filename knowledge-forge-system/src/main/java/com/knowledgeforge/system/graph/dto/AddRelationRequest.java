package com.knowledgeforge.system.graph.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddRelationRequest {
    @NotBlank
    private String sourceEntityName;
    @NotBlank
    private String targetEntityName;
    @NotBlank
    private String relationType;
    private String description;
}