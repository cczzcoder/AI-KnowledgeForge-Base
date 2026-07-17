package com.knowledgeforge.system.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "knowledgeforge.retrieval.keyword")
public class KeywordRetrievalProperties {

    private double fallbackScore = 0.1;
}
