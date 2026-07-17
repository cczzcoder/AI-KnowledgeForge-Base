package com.knowledgeforge.system.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "knowledgeforge.retrieval.hybrid")
public class HybridSearchProperties {

    private RrfProperties rrf = new RrfProperties();
    private WeightsProperties weights = new WeightsProperties();
    private ThresholdsProperties thresholds = new ThresholdsProperties();
    private AdjacentProperties adjacent = new AdjacentProperties();

    @Getter
    @Setter
    public static class RrfProperties {
        private double k = 60.0;
    }

    @Getter
    @Setter
    public static class WeightsProperties {
        private double vector = 0.7;
        private double keyword = 0.3;
        private double graph = 0.15;
    }

    @Getter
    @Setter
    public static class ThresholdsProperties {
        private double minHybridScore = 0.0115;
        private int minEvidenceHits = 2;
    }

    @Getter
    @Setter
    public static class AdjacentProperties {
        private int chunkWindow = 1;
        private int maxAdjacentChunks = 3;
        private int maxAdjacentChunksPerAnchor = 1;
    }
}
