package com.knowledgeforge.system.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CredibilityBreakdownDTO {
    private double similarityScore;
    private double freshnessScore;
    private double diversityScore;
    private double historyScore;
    private double overallScore;
}