package com.knowledgeforge.system.discovery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeGapDTO {
    private String topic;
    private double coverageDensity;
    private String coverageLevel;
    private List<String> missingSubtopics;
    private String suggestion;
}