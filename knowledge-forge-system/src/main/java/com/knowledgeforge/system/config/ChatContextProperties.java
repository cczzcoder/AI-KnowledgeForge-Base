package com.knowledgeforge.system.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "knowledgeforge.chat.context")
public class ChatContextProperties {

    private int maxChars = 12000;
    private int maxChunks = 8;
    private int maxChunksPerDocument = 3;
    private int sourceSnippetMaxChars = 150;
    private HighConfidenceProperties highConfidence = new HighConfidenceProperties();

    @Getter
    @Setter
    public static class HighConfidenceProperties {
        private int minEvidenceHits = 2;
        private double vectorSimilarityThreshold = 0.9;
        private double vectorHybridScoreThreshold = 0.011;
        private double keywordScoreThreshold = 0.28;
        private double keywordHybridScoreThreshold = 0.0115;
        private double graphScoreThreshold = 0.9;
        private double graphHybridScoreThreshold = 0.0105;
    }
}
