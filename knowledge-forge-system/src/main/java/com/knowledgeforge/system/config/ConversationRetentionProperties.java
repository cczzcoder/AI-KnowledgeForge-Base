package com.knowledgeforge.system.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "knowledgeforge.conversation")
public class ConversationRetentionProperties {

    private int retentionDays = 30;
    private int remindDaysBefore = 7;
    private String cleanupCron = "0 0 3 * * *";
}
