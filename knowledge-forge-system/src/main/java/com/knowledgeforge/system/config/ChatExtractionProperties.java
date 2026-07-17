package com.knowledgeforge.system.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "knowledgeforge.chat.extraction")
public class ChatExtractionProperties {

    private int autoExtractMaxCards = 3;
}
