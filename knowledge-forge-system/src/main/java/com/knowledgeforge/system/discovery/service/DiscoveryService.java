package com.knowledgeforge.system.discovery.service;

import com.knowledgeforge.core.entity.KnowledgeGraphEntity;
import com.knowledgeforge.system.discovery.dto.KnowledgeGapDTO;
import com.knowledgeforge.system.discovery.dto.LearningPathDTO;
import com.knowledgeforge.system.discovery.dto.RecommendationDTO;
import com.knowledgeforge.system.graph.repository.KnowledgeGraphEntityRepository;
import com.knowledgeforge.system.graph.repository.KnowledgeGraphRelationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscoveryService {

    private static final double COVERAGE_THRESHOLD = 0.3;
    private static final int MAX_GAPS = 10;

    private final KnowledgeGraphEntityRepository entityRepository;
    private final KnowledgeGraphRelationRepository relationRepository;

    @Transactional(readOnly = true)
    public List<KnowledgeGapDTO> detectKnowledgeGaps(UUID kbId) {
        List<KnowledgeGraphEntity> allEntities = entityRepository.findByKbId(kbId);
        if (allEntities.isEmpty()) {
            return List.of();
        }
        return detectGapsFromEntities(allEntities);
    }

    @Transactional(readOnly = true)
    public List<RecommendationDTO> getRecommendations(UUID kbId) {
        List<KnowledgeGraphEntity> allEntities = entityRepository.findByKbId(kbId);
        if (allEntities.isEmpty()) {
            return List.of();
        }

        List<KnowledgeGapDTO> gaps = detectGapsFromEntities(allEntities);
        List<RecommendationDTO> recommendations = new ArrayList<>();

        for (int i = 0; i < gaps.size(); i++) {
            KnowledgeGapDTO gap = gaps.get(i);
            recommendations.add(RecommendationDTO.builder()
                    .topic(gap.getTopic())
                    .reason("该领域知识覆盖密度为 " + gap.getCoverageDensity() + "，建议补充相关知识")
                    .suggestedResources(gap.getMissingSubtopics())
                    .priority(i + 1)
                    .build());
        }

        Map<String, Long> typeCounts = allEntities.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getEntityType() != null ? e.getEntityType() : "概念",
                        Collectors.counting()));

        typeCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3)
                .forEach(entry -> {
                    boolean alreadyRecommended = recommendations.stream()
                            .anyMatch(r -> r.getTopic().equals(entry.getKey()));
                    if (!alreadyRecommended) {
                        recommendations.add(RecommendationDTO.builder()
                                .topic(entry.getKey())
                                .reason("已积累 " + entry.getValue() + " 个实体，建议深入探索该领域")
                                .suggestedResources(List.of("搜索相关文档", "查看实体关系"))
                                .priority(recommendations.size() + 1)
                                .build());
                    }
                });

        return recommendations;
    }

    @Transactional(readOnly = true)
    public LearningPathDTO getLearningPath(UUID kbId, String topic) {
        if (topic == null || topic.isBlank()) {
            return null;
        }
        List<KnowledgeGraphEntity> entities = entityRepository.searchByName(kbId, topic);
        List<LearningPathDTO.LearningStepDTO> steps = new ArrayList<>();
        String summary;
        List<String> entityNames = new ArrayList<>();
        List<String> entityTypes = new ArrayList<>();
        List<String> entityDescExcerpts = new ArrayList<>();

        if (entities.isEmpty()) {
            summary = "针对「" + topic + "」主题的系统化学习路径。当前知识库中该主题暂无实体数据，以下学习路径基于通用知识体系构建，建议上传相关文档以获得更精准的个性化路径。";

            steps.add(LearningPathDTO.LearningStepDTO.builder()
                    .order(1)
                    .title("第一阶段：了解" + topic + "基础概念")
                    .description("建立对该主题的基本认知，掌握核心概念和术语")
                    .status("未覆盖")
                    .knowledgePoints(List.of(
                            topic + "的定义与核心概念",
                            topic + "的产生背景与发展历程",
                            topic + "的核心组成要素",
                            topic + "的分类方式与评判标准"))
                    .recommendedResources(List.of(
                            "建议上传" + topic + "相关的入门级文档到知识库",
                            "使用RAG对话询问「" + topic + "是什么」获取概览",
                            "搜索已有知识库中关联主题的实体和关系"))
                    .expectedOutcomes(List.of(
                            "能够清晰描述" + topic + "的定义和核心特征",
                            "了解该主题在整体知识体系中的位置"))
                    .build());

            steps.add(LearningPathDTO.LearningStepDTO.builder()
                    .order(2)
                    .title("第二阶段：探索" + topic + "关联知识")
                    .description("深入理解该主题与其他知识领域的关联和交叉")
                    .status("待探索")
                    .knowledgePoints(List.of(
                            topic + "与相关领域的关联关系",
                            topic + "在实际场景中的具体应用",
                            "不同视角下的" + topic + "理解差异"))
                    .recommendedResources(List.of(
                            "上传" + topic + "相关的中高级文档",
                            "通过知识图谱搜索发现关联实体",
                            "在对话中提问「" + topic + "与XX的关系是什么」"))
                    .expectedOutcomes(List.of(
                            "能够举例说明" + topic + "在3个及以上场景中的应用",
                            "能够梳理" + topic + "与至少2个关联领域的交叉关系"))
                    .build());

            steps.add(LearningPathDTO.LearningStepDTO.builder()
                    .order(3)
                    .title("第三阶段：实践" + topic + "应用与分析")
                    .description("将所学知识应用到实际问题解决中，通过深度问答检验学习效果")
                    .status("待实践")
                    .knowledgePoints(List.of(
                            topic + "的深度分析方法论",
                            "基于" + topic + "解决实际问题的路径",
                            topic + "领域的前沿进展和趋势"))
                    .recommendedResources(List.of(
                            "使用RAG对话功能进行" + topic + "深度问答",
                            "整理个人学习笔记并上传至知识库",
                            "在对话中尝试解决与" + topic + "相关的实际问题"))
                    .expectedOutcomes(List.of(
                            "能够独立完成" + topic + "相关问题的深度分析",
                            "能够在对话中准确回答" + topic + "领域的专业问题"))
                    .build());
        } else {
            for (var e : entities) {
                entityNames.add(e.getName());
                if (e.getEntityType() != null && !entityTypes.contains(e.getEntityType())) {
                    entityTypes.add(e.getEntityType());
                }
                if (e.getDescription() != null && !e.getDescription().isBlank()) {
                    String excerpt = e.getDescription().length() > 80
                            ? e.getDescription().substring(0, 80) + "…"
                            : e.getDescription();
                    entityDescExcerpts.add(excerpt);
                }
            }

            String entityList = String.join("、", entityNames.stream().limit(8).toList());
            String typeList = entityTypes.isEmpty() ? "知识实体" : String.join("、", entityTypes);
            long totalEntityCount = entityRepository.findByKbId(kbId).size();

            summary = "针对「" + topic + "」的系统化学习路径。该知识库目前已收录 " + entities.size()
                    + " 个与「" + topic + "」直接相关的实体（含 " + typeList + " 等类型），"
                    + "知识库共有 " + totalEntityCount + " 个实体。以下学习路径基于已有知识库数据构建。";

            KnowledgeGraphEntity primaryEntity = entities.get(0);
            var relations = relationRepository.findRelationsByKbIdAndEntityId(kbId, primaryEntity.getId());
            long relatedCount = relations.size();

            List<String> relatedEntityNames = new ArrayList<>();
            for (var rel : relations) {
                UUID relatedId = rel.getSourceEntityId().equals(primaryEntity.getId())
                        ? rel.getTargetEntityId() : rel.getSourceEntityId();
                entities.stream()
                        .filter(e -> e.getId().equals(relatedId))
                        .findFirst()
                        .ifPresent(e -> relatedEntityNames.add(e.getName()));
            }

            steps.add(LearningPathDTO.LearningStepDTO.builder()
                    .order(1)
                    .title("第一阶段：掌握" + topic + "已有知识")
                    .description("知识库中已收录 " + entities.size() + " 个与「" + topic
                            + "」相关的实体，覆盖 " + typeList + " 等" + entityTypes.size() + " 种类型")
                    .status("已覆盖")
                    .knowledgePoints(List.of(
                            entityList.isEmpty() ? topic + "的核心概念" : "已收录实体： " + entityList,
                            "实体类型分布：" + typeList,
                            entityDescExcerpts.isEmpty() ? "" : "已有描述摘要：" + String.join("；", entityDescExcerpts.stream().limit(3).toList())))
                    .recommendedResources(List.of(
                            "在知识图谱页面搜索「" + topic + "」查看实体详情",
                            "使用图谱可视化功能查看实体间的关联关系",
                            "对描述不完整的实体，建议补充详细描述信息"))
                    .expectedOutcomes(List.of(
                            "能够准确说出「" + topic + "」下至少" + Math.min(5, entities.size()) + "个关键实体的名称和含义",
                            "理解各实体之间的类型分布和层级关系"))
                    .build());

            steps.add(LearningPathDTO.LearningStepDTO.builder()
                    .order(2)
                    .title("第二阶段：探索" + topic + "关联关系")
                    .description(relatedCount > 0
                            ? "已发现 " + relatedCount + " 条关联关系，连接了 "
                            + (relatedEntityNames.isEmpty() ? entities.size() : relatedEntityNames.size()) + " 个关联实体"
                            : "该实体暂无显式关联关系，建议深入探索")
                    .status(relatedCount > 0 ? "可探索" : "待补充")
                    .knowledgePoints(relatedCount > 0 ? List.of(
                            "关联实体名称：" + String.join("、", relatedEntityNames.stream().limit(8).toList()),
                            "关系总数：" + relatedCount + " 条",
                            "可通过图谱查询进一步发现隐含关系")
                            : List.of(
                                    "当前缺少关联关系数据",
                                    "建议上传更多文档以建立实体关联",
                                    "可在对话中尝试分析潜在的隐含关系"))
                    .recommendedResources(List.of(
                            "点击实体名称查看实体详情页面",
                            "使用子图功能以「" + primaryEntity.getName() + "」为中心查看局部图谱",
                            "在RAG对话中提问「" + primaryEntity.getName() + "与XX的关系」"))
                    .expectedOutcomes(List.of(
                            "能够梳理「" + topic + "」相关的核心关系网络",
                            "能够举例说明至少3个实体之间的关联方式"))
                    .build());

            steps.add(LearningPathDTO.LearningStepDTO.builder()
                    .order(3)
                    .title("第三阶段：深度问答与实践应用")
                    .description("基于" + entities.size() + "个实体和" + relatedCount + "条关系的知识储备，进行深度问答实践")
                    .status("可实践")
                    .knowledgePoints(List.of(
                            "综合运用已有实体和关系进行推理",
                            "通过RAG对话检索" + topic + "相关文档片段",
                            "在实践中发现知识盲区并补充"))
                    .recommendedResources(List.of(
                            "在对话页面选择对应知识库，开始" + topic + "深度问答",
                            "针对问答中遇到的盲区，上传补充文档",
                            "使用智能推荐功能发现需要补充的知识领域"))
                    .expectedOutcomes(List.of(
                            "能够在RAG对话中准确回答" + topic + "相关的专业问题",
                            "能够发现并补充至少1个知识盲区"))
                    .build());
        }

        LearningPathDTO.LearningExampleDTO example = buildExample(topic, entities);

        return LearningPathDTO.builder()
                .topic(topic)
                .summary(summary)
                .steps(steps)
                .example(example)
                .build();
    }

    private LearningPathDTO.LearningExampleDTO buildExample(String topic,
                                                             List<KnowledgeGraphEntity> entities) {
        if (!entities.isEmpty() && entities.size() >= 3) {
            KnowledgeGraphEntity e1 = entities.get(0);
            KnowledgeGraphEntity e2 = entities.get(1);
            return buildConcreteExample(e1.getName(), e1.getEntityType(), e2.getName(), entities);
        }

        // 默认提供「西游记」知识专题的详细示例
        return buildDefaultXiyoujiExample();
    }

    /**
     * 基于知识库已有实体构建具体示例。
     */
    private LearningPathDTO.LearningExampleDTO buildConcreteExample(
            String primaryName, String primaryType, String secondaryName,
            List<KnowledgeGraphEntity> entities) {
        List<String> coreEntityNames = entities.stream()
                .map(KnowledgeGraphEntity::getName)
                .limit(5)
                .toList();

        return LearningPathDTO.LearningExampleDTO.builder()
                .topic(primaryName + "知识专题")
                .overallGoal("通过三个阶段系统学习，全面掌握「" + primaryName + "」的核心内容及"
                        + String.join("、", coreEntityNames) + " 等实体间的关联，达到能够独立深度问答的水平")
                .exampleSteps(List.of(
                        LearningPathDTO.LearningExampleDTO.ExampleStepDTO.builder()
                                .stage(1)
                                .stageName("基础认知阶段：了解" + primaryName)
                                .objective("建立对「" + primaryName + "」基本认知，掌握核心定义和关键要素")
                                .coreContents(List.of(
                                        primaryName + "的定义、背景与" + (primaryType != null ? primaryType : "基本信息"),
                                        "关键人物/关键实体：" + String.join("、", coreEntityNames),
                                        "基本分类与整体结构框架"))
                                .duration("建议 2-3 次学习会话")
                                .outcome("能够准确描述「" + primaryName + "」的核心概念和至少3个关键实体的含义")
                                .build(),
                        LearningPathDTO.LearningExampleDTO.ExampleStepDTO.builder()
                                .stage(2)
                                .stageName("关联探索阶段：分析" + primaryName + "的关系网络")
                                .objective("深入理解「" + primaryName + "」与" + secondaryName + " 等实体间的关联关系")
                                .coreContents(List.of(
                                        "核心实体间的关联关系类型与分析",
                                        primaryName + "在不同维度下的交叉对比",
                                        "利用知识图谱可视化探索隐含关系"))
                                .duration("建议 3-4 次学习会话")
                                .outcome("能够绘制" + primaryName + "的关系网络图，解释至少5条实体间的关系逻辑")
                                .build(),
                        LearningPathDTO.LearningExampleDTO.ExampleStepDTO.builder()
                                .stage(3)
                                .stageName("深度应用阶段：基于" + primaryName + "的综合分析")
                                .objective("将所学知识应用于实际问答、分析和知识盲区发现")
                                .coreContents(List.of(
                                        "综合运用知识库进行RAG深度对话",
                                        "针对" + primaryName + "相关专题撰写分析报告",
                                        "发现并补充知识盲区，扩展知识边界"))
                                .duration("建议 4-6 次学习会话")
                                .outcome("能够独立完成" + primaryName + "领域的深度问答，识别并填补至少3个知识盲区")
                                .build()))
                .build();
    }

    /**
     * 提供「西游记」经典文学作品的详细学习路径示例，
     * 确保用户能够直观理解并有效使用学习路径功能。
     */
    private LearningPathDTO.LearningExampleDTO buildDefaultXiyoujiExample() {
        return LearningPathDTO.LearningExampleDTO.builder()
                .topic("《西游记》知识专题")
                .overallGoal("通过三阶段系统学习，全面掌握《西游记》的核心内容——包括作者背景（吴承恩）、"
                        + "主要人物（唐僧、孙悟空、猪八戒、沙僧、白龙马）、经典故事情节（大闹天宫、三打白骨精、"
                        + "真假美猴王等）、文化内涵（儒释道三教融合）以及文学价值，最终能够在RAG对话中进行深度问答和分析")
                .exampleSteps(List.of(
                        LearningPathDTO.LearningExampleDTO.ExampleStepDTO.builder()
                                .stage(1)
                                .stageName("基础认知阶段：了解《西游记》全貌")
                                .objective("建立对《西游记》的基本认知框架，掌握作者、成书背景、主要人物和故事主线")
                                .coreContents(List.of(
                                        "作者吴承恩的生平与创作背景（明代小说家，约1500-1582年）",
                                        "《西游记》的成书过程与版本演变（世德堂本、李卓吾评本等）",
                                        "核心人物谱系：唐僧（金蝉子转世）、孙悟空（齐天大圣）、猪八戒（天蓬元帅）、沙僧（卷帘大将）、白龙马（西海龙王三太子）",
                                        "故事主线：唐朝高僧玄奘西天取经，历经九九八十一难",
                                        "文学地位：中国古典四大名著之一，神魔小说巅峰之作"))
                                .duration("建议 2-3 次学习会话，每次 30-60 分钟")
                                .outcome("能够准确说出《西游记》的作者、成书年代、5位主要人物及其背景，"
                                        + "概述取经主线故事")
                                .build(),
                        LearningPathDTO.LearningExampleDTO.ExampleStepDTO.builder()
                                .stage(2)
                                .stageName("关联探索阶段：深入人物关系与经典情节")
                                .objective("深入理解人物之间的复杂关系、经典故事情节及其文化寓意")
                                .coreContents(List.of(
                                        "师徒关系分析：唐僧与三位徒弟的关系演变（从收服到磨合到默契）",
                                        "经典情节研读：大闹天宫、三打白骨精、真假美猴王、三借芭蕉扇、女儿国等",
                                        "神魔体系：天庭（玉帝、太上老君）、西天（如来、观音）、妖怪（牛魔王、铁扇公主、红孩儿等）",
                                        "人物性格深度分析：孙悟空的叛逆与成长、猪八戒的人性弱点与真实、唐僧的慈悲与软弱",
                                        "儒释道三教文化融合在书中体现"))
                                .duration("建议 3-5 次学习会话，每次 45-90 分钟")
                                .outcome("能够绘制人物关系图谱，深入分析至少5个经典情节的文化意义，"
                                        + "解释人物性格与成长轨迹")
                                .build(),
                        LearningPathDTO.LearningExampleDTO.ExampleStepDTO.builder()
                                .stage(3)
                                .stageName("深度应用阶段：文学批评与RAG问答实践")
                                .objective("将知识体系应用于文学分析、批评讨论和深度问答")
                                .coreContents(List.of(
                                        "主题分析：取经的象征意义（心性修炼、佛教传播、文化交流等视角）",
                                        "比较文学视角：《西游记》与《大唐西域记》的关系、与其他三大名著的对比",
                                        "《西游记》对后世文学、戏曲、影视、动漫的影响（如大话西游、黑神话悟空等）",
                                        "通过RAG对话进行深度问答：检验知识点掌握，发现并填补知识盲区",
                                        "撰写个人分析笔记：选定一个专题深入分析并上传至知识库"))
                                .duration("建议 4-6 次学习会话，每次 45-90 分钟")
                                .outcome("能够独立完成《西游记》相关专题的深度分析，"
                                        + "在RAG对话中准确回答专业问题，具备文学批评的基本能力")
                                .build()))
                .build();
    }

    private List<KnowledgeGapDTO> detectGapsFromEntities(List<KnowledgeGraphEntity> allEntities) {
        Map<String, List<KnowledgeGraphEntity>> entitiesByType = allEntities.stream()
                .collect(Collectors.groupingBy(e ->
                        e.getEntityType() != null ? e.getEntityType() : "概念"));

        List<KnowledgeGapDTO> gaps = new ArrayList<>();

        for (var entry : entitiesByType.entrySet()) {
            String type = entry.getKey();
            List<KnowledgeGraphEntity> typeEntities = entry.getValue();

            double coverageScore = calculateTypeCoverage(type, typeEntities, allEntities.size());

            if (coverageScore < COVERAGE_THRESHOLD) {
                List<String> missingSubtopics = inferMissingSubtopics(type, typeEntities);

                gaps.add(KnowledgeGapDTO.builder()
                        .topic(type)
                        .coverageDensity(Math.round(coverageScore * 100.0) / 100.0)
                        .coverageLevel(coverageScore < 0.1 ? "严重不足" : "不足")
                        .missingSubtopics(missingSubtopics)
                        .suggestion("建议上传更多关于" + type + "领域的文档，丰富知识覆盖")
                        .build());
            }
        }

        if (gaps.isEmpty()) {
            detectSparseEntities(allEntities, gaps);
        }

        return gaps.stream().limit(MAX_GAPS).toList();
    }

    private double calculateTypeCoverage(String type, List<KnowledgeGraphEntity> typeEntities, int totalEntities) {
        if (totalEntities == 0) {
            return 0;
        }

        double ratio = (double) typeEntities.size() / totalEntities;

        long entitiesWithDescription = typeEntities.stream()
                .filter(e -> e.getDescription() != null && !e.getDescription().isBlank())
                .count();
        double descriptionRatio = typeEntities.isEmpty() ? 0
                : (double) entitiesWithDescription / typeEntities.size();

        return ratio * 0.6 + descriptionRatio * 0.4;
    }

    private List<String> inferMissingSubtopics(String type, List<KnowledgeGraphEntity> typeEntities) {
        List<String> subtopics = new ArrayList<>();

        if (typeEntities.isEmpty()) {
            subtopics.add(type + "基础概念");
            subtopics.add(type + "应用场景");
            subtopics.add(type + "最佳实践");
            return subtopics;
        }

        long withDescription = typeEntities.stream()
                .filter(e -> e.getDescription() != null && !e.getDescription().isBlank())
                .count();
        if (withDescription < typeEntities.size() * 0.5) {
            subtopics.add("完善实体描述信息");
        }

        if (typeEntities.size() < 3) {
            subtopics.add("扩展" + type + "相关实体");
        }

        if (subtopics.isEmpty()) {
            subtopics.add("探索" + type + "领域前沿知识");
        }

        return subtopics;
    }

    private void detectSparseEntities(List<KnowledgeGraphEntity> allEntities,
                                       List<KnowledgeGapDTO> gaps) {
        Map<String, Long> nameFrequency = new HashMap<>();
        Map<String, String> nameTypes = new HashMap<>();
        for (var entity : allEntities) {
            String name = entity.getName();
            if (name != null && name.length() >= 2) {
                nameFrequency.merge(name, 1L, Long::sum);
                if (entity.getEntityType() != null) {
                    nameTypes.putIfAbsent(name, entity.getEntityType());
                }
            }
        }

        // 合并子串实体计数：若存在"吴承"和"吴承恩"，将"吴承"的计数归入"吴承恩"
        List<String> namesToRemove = new ArrayList<>();
        for (var entry : new HashMap<>(nameFrequency).entrySet()) {
            String name = entry.getKey();
            for (var otherEntry : nameFrequency.entrySet()) {
                String otherName = otherEntry.getKey();
                if (otherName.length() > name.length() && otherName.contains(name)) {
                    // name 是 otherName 的子串，合并计数
                    nameFrequency.merge(otherName, entry.getValue(), Long::sum);
                    nameTypes.putIfAbsent(otherName, nameTypes.getOrDefault(name, "未知"));
                    namesToRemove.add(name);
                    log.debug("高频词合并: '{}'({}次) 归入 '{}'", name, entry.getValue(), otherName);
                    break;
                }
            }
        }
        namesToRemove.forEach(nameFrequency::remove);

        var frequentNames = nameFrequency.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .toList();

        for (int i = 0; i < Math.min(frequentNames.size(), 3); i++) {
            var entry = frequentNames.get(i);
            String entityType = nameTypes.getOrDefault(entry.getKey(), "未知");
            gaps.add(KnowledgeGapDTO.builder()
                    .topic("'" + entry.getKey() + "'（" + entityType + "）相关领域")
                    .coverageDensity(Math.min(0.3, entry.getValue() * 0.05))
                    .coverageLevel("待扩展")
                    .missingSubtopics(List.of(
                            "完善'" + entry.getKey() + "'的详细描述和关联信息",
                            "探索与'" + entry.getKey() + "'相关的其他实体和关系")
                    )
                    .suggestion("'" + entry.getKey() + "'在知识图谱中出现 " + entry.getValue()
                            + " 次，建议扩充该实体的关联知识和背景信息")
                    .build());
        }
    }
}