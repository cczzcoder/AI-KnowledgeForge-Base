package com.knowledgeforge.system.conversation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationRetentionScheduler {

    private final ConversationService conversationService;

    @Scheduled(cron = "${knowledgeforge.conversation.cleanup-cron:0 0 3 * * *}")
    public void applyRetentionPolicies() {
        int marked = conversationService.applyRetentionPolicies();
        int purged = conversationService.purgeExpiredConversations();
        if (marked > 0 || purged > 0) {
            log.info("对话保留策略执行完成: marked={}, purged={}", marked, purged);
        }
    }
}
