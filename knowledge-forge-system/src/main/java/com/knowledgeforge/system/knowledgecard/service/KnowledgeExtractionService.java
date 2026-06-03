package com.knowledgeforge.system.knowledgecard.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgeforge.core.entity.ChatMessage;
import com.knowledgeforge.core.entity.KnowledgeCard;
import com.knowledgeforge.system.conversation.repository.ChatMessageRepository;
import com.knowledgeforge.system.knowledgecard.dto.KnowledgeCardDTO;
import com.knowledgeforge.system.knowledgecard.repository.KnowledgeCardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 对话知识提取服务 — 从已完成的对话中自动提取可沉淀的知识卡片。
 * 使用LLM分析对话内容，识别关键知识点并结构化为KnowledgeCard。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeExtractionService {

    private final ChatClient chatClient;
    private final ChatMessageRepository chatMessageRepository;
    private final KnowledgeCardRepository knowledgeCardRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 知识提取的LLM提示词模板
     */
    static final String EXTRACTION_PROMPT = """
            你是一个知识沉淀专家。请从以下对话记录中提取有价值的知识点，并将其结构化为知识卡片。
            
            提取要求：
            1. 识别对话中讨论的核心概念、重要事实、规则原理、独特见解
            2. 每个知识点总结为一张知识卡片，包含标题和详细内容
            3. 内容应精炼、准确，去除对话中的冗余信息
            4. 只提取有明确价值的、可复用的知识点，忽略闲聊和无关内容
            5. 最多提取 %d 个知识点
            
            知识分类标准：
            - CONCEPT（概念）: 对术语、概念的定义和解释
            - FACT（事实）: 客观陈述的事实信息
            - RULE（规则）: 方法论、步骤、规则、原理
            - INSIGHT（见解）: 分析性观点、独到见解、经验总结
            
            返回格式（严格JSON）：
            {
              "cards": [
                {
                  "title": "知识卡片标题",
                  "content": "知识卡片详细内容",
                  "category": "CONCEPT/FACT/RULE/INSIGHT",
                  "entityType": "可选：对应的图谱实体类型（人物/地点/概念/事件/作品/组织）"
                }
              ],
              "summary": "本轮提取结果的简要说明（一句话）"
            }
            
            对话记录：
            %s
            """;

    /**
     * 从指定会话中提取知识卡片。
     * 分析会话中的所有消息，识别可沉淀的知识点，保存为PENDING状态的卡片等待审核。
     *
     * @param kbId          知识库ID
     * @param conversationId 会话ID
     * @param maxCards      最多提取卡片数
     * @return 提取结果
     */
    @Transactional
    public KnowledgeCardDTO.ExtractionResult extractFromConversation(UUID kbId, UUID conversationId, int maxCards) {
        List<ChatMessage> messages = chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        int messageCount = messages.size();

        if (messages.isEmpty()) {
            return KnowledgeCardDTO.ExtractionResult.builder()
                    .cards(Collections.emptyList())
                    .messagesAnalyzed(0)
                    .summary("会话中没有消息记录")
                    .build();
        }

        // 构建对话文本用于LLM分析
        String conversationText = buildConversationText(messages);

        // 调用LLM提取知识
        List<KnowledgeCard> extracted = extractWithLLM(conversationText, maxCards);

        // 保存卡片到数据库
        List<KnowledgeCardDTO> savedCards = new ArrayList<>();
        String summary = "本轮未提取到有价值的知识点";
        if (!extracted.isEmpty()) {
            for (KnowledgeCard card : extracted) {
                card.setKbId(kbId);
                card.setConversationId(conversationId);
                card.setStatus(KnowledgeCard.Status.PENDING.name());
                KnowledgeCard saved = knowledgeCardRepository.save(card);
                savedCards.add(toDTO(saved));
            }
            summary = "成功提取 " + extracted.size() + " 个知识点，已保存为PENDING状态等待审核";
        }

        log.info("知识提取完成: kbId={}, conversationId={}, 分析{}条消息, 提取{}个知识点",
                kbId, conversationId, messageCount, extracted.size());

        return KnowledgeCardDTO.ExtractionResult.builder()
                .cards(savedCards)
                .messagesAnalyzed(messageCount)
                .summary(summary)
                .build();
    }

    /**
     * 将对话消息格式化为LLM可理解的文本
     */
    private String buildConversationText(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            String role = "USER".equals(msg.getRole()) ? "用户" : "助手";
            // 截断过长的消息内容
            String content = msg.getContent();
            if (content != null && content.length() > 1000) {
                content = content.substring(0, 1000) + "...";
            }
            sb.append("[").append(i + 1).append("] ").append(role).append(": ").append(content).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 调用LLM进行知识提取
     */
    private List<KnowledgeCard> extractWithLLM(String conversationText, int maxCards) {
        String prompt = String.format(EXTRACTION_PROMPT, maxCards, conversationText);

        try {
            var aiResponse = chatClient.prompt(new Prompt(List.of(
                    new SystemMessage("你是一个知识沉淀专家。请严格按照JSON格式返回结果，确保每个知识点的标题和内容完整准确。"),
                    new UserMessage(prompt)
            ))).call().chatResponse();

            String responseText = aiResponse != null
                    && aiResponse.getResult() != null
                    && aiResponse.getResult().getOutput() != null
                    ? aiResponse.getResult().getOutput().getText()
                    : "{}";

            String json = extractJson(responseText);
            Map<String, Object> result = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cardsData = (List<Map<String, Object>>) result.getOrDefault("cards",
                    Collections.emptyList());

            return cardsData.stream().map(this::parseCard).filter(Objects::nonNull).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("LLM知识提取失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 将LLM返回的JSON数据解析为KnowledgeCard
     */
    private KnowledgeCard parseCard(Map<String, Object> data) {
        try {
            String title = (String) data.get("title");
            String content = (String) data.get("content");
            if (title == null || title.isBlank() || content == null || content.isBlank()) {
                return null;
            }
            String category = (String) data.getOrDefault("category", "CONCEPT");
            String entityType = (String) data.get("entityType");

            // 标准化分类值
            category = normalizeCategory(category);

            return KnowledgeCard.builder()
                    .title(title.trim())
                    .content(content.trim())
                    .category(category)
                    .entityType(entityType)
                    .build();
        } catch (Exception e) {
            log.warn("解析知识卡片失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 标准化分类枚举值
     */
    private String normalizeCategory(String raw) {
        if (raw == null) return "CONCEPT";
        return switch (raw.toUpperCase()) {
            case "FACT" -> "FACT";
            case "RULE" -> "RULE";
            case "INSIGHT" -> "INSIGHT";
            default -> "CONCEPT";
        };
    }

    /**
     * 从JSON字符串中提取第一段合法JSON
     */
    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return "{}";
    }

    /**
     * 实体转DTO
     */
    private KnowledgeCardDTO toDTO(KnowledgeCard card) {
        return KnowledgeCardDTO.builder()
                .id(card.getId())
                .kbId(card.getKbId())
                .conversationId(card.getConversationId())
                .messageId(card.getMessageId())
                .title(card.getTitle())
                .content(card.getContent())
                .category(card.getCategory())
                .status(card.getStatus())
                .entityType(card.getEntityType())
                .sourceContext(card.getSourceContext())
                .reviewerNote(card.getReviewerNote())
                .vectorized(card.getVectorized())
                .graphEntityId(card.getGraphEntityId())
                .createdAt(card.getCreatedAt())
                .build();
    }
}