package com.knowledgeforge.system.graph.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgeforge.system.graph.dto.EntityExtractionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EntityExtractionService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${knowledgeforge.extraction.max-chunks:10}")
    private int maxChunks;

    @Value("${knowledgeforge.extraction.rate-limit-ms:500}")
    private long rateLimitMs;

    private static final String EXTRACTION_PROMPT = """
            你是一个知识图谱实体关系抽取专家。请从以下文本中提取实体和关系，以 JSON 格式返回。
            
            要求：
            1. 实体(entity)包括：人物、地点、组织、概念、事件、时间、作品等
            2. **重要规则：必须提取完整实体名称，不能截断。例如文中出现"吴承恩"，必须提取为完整实体"吴承恩"，绝不能提取为不完整的"吴承"或"承恩"**
            3. **专有名词保护：专有名词、人名、地名、作品名等必须整体提取，不得按字拆分，必须保留完整名称**
            4. 每个实体提供：name(名称)、entityType(类型)、description(简短描述)、aliases(别名列表)
            5. 关系(relation)包括实体之间的关联：属于、位于、作者、包含、参与、创建、关联等
            6. 每个关系提供：sourceEntity(源实体名称)、targetEntity(目标实体名称)、relationType(关系类型)、description(描述)
            7. 只提取文中明确提到的实体和关系，不要编造
            
            返回格式（严格JSON）：
            {
              "entities": [
                {"name": "实体名", "entityType": "人物", "description": "描述", "aliases": ["别名1"]}
              ],
              "relations": [
                {"sourceEntity": "实体A", "targetEntity": "实体B", "relationType": "属于", "description": "描述"}
              ]
            }
            
            文本内容：
            %s
            """;

    public EntityExtractionResult extractEntities(String text) {
        if (text == null || text.isBlank()) {
            return EntityExtractionResult.builder()
                    .entities(Collections.emptyList())
                    .relations(Collections.emptyList())
                    .build();
        }

        String truncatedText = text.length() > 3000 ? text.substring(0, 3000) : text;
        String prompt = String.format(EXTRACTION_PROMPT, truncatedText);

        try {
            var aiResponse = chatClient.prompt(new Prompt(List.of(
                    new SystemMessage("你是一个知识图谱实体关系抽取专家。请严格按照JSON格式返回结果。"),
                    new UserMessage(prompt)
            ))).call().chatResponse();

            String responseText = aiResponse != null
                    && aiResponse.getResult() != null
                    && aiResponse.getResult().getOutput() != null
                    ? aiResponse.getResult().getOutput().getText()
                    : "{}";

            String json = extractJson(responseText);
            EntityExtractionResult result = objectMapper.readValue(json, EntityExtractionResult.class);

            if (result.getEntities() == null) {
                result.setEntities(Collections.emptyList());
            }
            if (result.getRelations() == null) {
                result.setRelations(Collections.emptyList());
            }

            // 后处理：合并子串实体，确保完整名称不被截断（如"吴承恩"不应被拆为"吴承"）
            result.setEntities(mergeSubstringEntities(result.getEntities()));

            log.info("实体抽取完成: {} 个实体, {} 个关系", result.getEntities().size(), result.getRelations().size());
            return result;
        } catch (Exception e) {
            log.warn("实体抽取失败: {}", e.getMessage());
            return EntityExtractionResult.builder()
                    .entities(Collections.emptyList())
                    .relations(Collections.emptyList())
                    .build();
        }
    }

    public EntityExtractionResult extractEntities(List<String> chunks) {
        EntityExtractionResult merged = EntityExtractionResult.builder()
                .entities(new java.util.ArrayList<>())
                .relations(new java.util.ArrayList<>())
                .build();

        int processed = 0;
        int limit = Math.min(chunks.size(), maxChunks);
        for (int i = 0; i < limit; i++) {
            EntityExtractionResult result = extractEntities(chunks.get(i));
            if (result.getEntities() != null) {
                merged.getEntities().addAll(result.getEntities());
            }
            if (result.getRelations() != null) {
                merged.getRelations().addAll(result.getRelations());
            }
            processed++;
            if (i < limit - 1 && rateLimitMs > 0) {
                try {
                    Thread.sleep(rateLimitMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.info("批量实体抽取完成: {} 个chunk -> {} 个实体, {} 个关系",
                processed, merged.getEntities().size(), merged.getRelations().size());
        return merged;
    }

    /**
     * 合并子串实体：当存在两个实体名称，其中一个是另一个的子串时，
     * 只保留较长的完整名称（如"吴承恩"和"吴承"同时存在时，只保留"吴承恩"）。
     * 同时更新关系中的引用。
     */
    private List<EntityExtractionResult.ExtractedEntity> mergeSubstringEntities(
            List<EntityExtractionResult.ExtractedEntity> entities) {
        if (entities == null || entities.size() < 2) {
            return entities;
        }

        List<EntityExtractionResult.ExtractedEntity> merged = new java.util.ArrayList<>();
        java.util.Set<String> toRemove = new java.util.HashSet<>();

        for (int i = 0; i < entities.size(); i++) {
            var ei = entities.get(i);
            if (ei.getName() == null || ei.getName().length() < 2) {
                continue;
            }
            boolean isSubstring = false;
            for (int j = 0; j < entities.size(); j++) {
                if (i == j) continue;
                var ej = entities.get(j);
                if (ej.getName() == null) continue;
                // 如果 ei 是 ej 的真子串，标记 ei 为待移除
                if (ej.getName().length() > ei.getName().length()
                        && ej.getName().contains(ei.getName())) {
                    isSubstring = true;
                    log.debug("实体合并: 移除子串'{}', 保留完整名称'{}'", ei.getName(), ej.getName());
                    break;
                }
            }
            if (!isSubstring) {
                merged.add(ei);
            } else {
                toRemove.add(ei.getName());
            }
        }

        return merged;
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return "{}";
    }
}