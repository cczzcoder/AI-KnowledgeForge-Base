package com.knowledgeforge.system.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "knowledgeforge.chat.credibility")
public class CredibilityProperties {

    private double similarityWeight = 0.4;
    private double freshnessWeight = 0.2;
    private double diversityWeight = 0.2;
    private double historyWeight = 0.2;
    private int freshnessDecayDays = 90;
}
