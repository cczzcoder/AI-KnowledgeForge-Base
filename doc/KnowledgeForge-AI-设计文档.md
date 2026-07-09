# KnowledgeForge AI 个人知识库问答系统 — 设计文档

> **版本：** v1.3（已根据 Spring AI 1.0 GA 官方文档 + 2025 RAG 最佳实践审查修订）\
> **作者：** PM Agent\
> **日期：** 2026-05-30\
> **参考：** [Spring AI 1.0 官方文档](https://docs.spring.io/spring-ai/reference/) / [pgvector 文档](https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html) / [Elasticsearch + Spring AI RAG](https://www.elastic.co/search-labs/blog/spring-ai-elasticsearch-application) / RAG 技术白皮书 / know-hub-ai 项目分析

***

## 目录

1. [项目概述](#1-项目概述)
2. [与 know-hub-ai 的差异化创新点](#2-与-know-hub-ai-的差异化创新点)
3. [系统架构设计](#3-系统架构设计)
4. [技术选型](#4-技术选型)
5. [核心模块详细设计](#5-核心模块详细设计)
6. [数据库设计](#6-数据库设计)
7. [API 接口设计](#7-api-接口设计)
8. [前端页面规划](#8-前端页面规划)
9. [部署架构](#9-部署架构)
10. [开发排期建议](#10-开发排期建议)

***

## 1. 项目概述

### 1.1 项目背景

随着大语言模型（LLM）的普及，个人知识管理进入了 AI 增强时代。传统的笔记软件只能「存储」知识，而无法「理解」和「对话」。RAG（Retrieval-Augmented Generation，检索增强生成）技术使得 AI 能够基于用户私有知识库进行精准问答，解决了大模型的「知识时效性限制」和「幻觉」问题。

### 1.2 项目定位

**KnowledgeForge AI**（知识熔炉）是一款基于 Spring AI 框架的个人知识库智能问答系统。它不仅实现了基础的 RAG 问答，更在**知识图谱增强检索**、**可信度溯源**、**自适应语义分块**、**增量学习**等方面进行深度创新，旨在打造一个真正「懂你知识」的 AI 助手。

### 1.3 核心目标

| 目标   | 描述                         |
| ---- | -------------------------- |
| 精准问答 | 基于用户私有知识库，提供准确、有源可溯的 AI 回答 |
| 知识关联 | 通过知识图谱自动发现知识间的实体关系，实现推理式检索 |
| 可信溯源 | 每个回答附带来源追溯链，标明出自哪个文档的哪个段落  |
| 持续进化 | 对话中产生的新知识自动沉淀入库，知识库随使用不断增长 |
| 主动服务 | 基于用户提问行为，主动推荐知识盲区和关联知识     |

***

## 2. 与 know-hub-ai 的差异化创新点

> know-hub-ai 是一个优秀的 Spring AI + RAG 学习项目，其核心创新在于：多模态对话、知识库分离管理、Minio 文件存储、文档图片提取描述。本方案在此基础上走向不同方向。

### 2.1 创新点对比矩阵

| 维度       | know-hub-ai       | **KnowledgeForge AI（本方案）**              |
| -------- | ----------------- | --------------------------------------- |
| **检索策略** | 纯向量语义检索           | **多策略混合检索**（BM25 关键词 + 向量语义 + 知识图谱推理）   |
| **知识组织** | 知识库隔离（文件夹式）       | **知识图谱组织**（实体-关系-实体，支持图遍历推理）            |
| **文档分块** | 依赖 Spring AI 默认分块 | **自适应语义分块**（基于标题层级 + 段落语义完整性动态分块）       |
| **可信度**  | 无评分机制             | **知识可信度溯源**（来源追溯链 + 多维度可信度评分）           |
| **知识增长** | 纯手动上传             | **增量学习**（对话新知识自动沉淀，用户审核后入库）             |
| **主动服务** | 被动问答              | **主动知识发现**（基于知识图谱盲区检测，主动推荐未覆盖知识）        |
| **模型适配** | 单一 Embedding 模型   | **多模型热插拔**（支持运行时切换 Embedding / Chat 模型） |
| **反馈闭环** | 无                 | **答案质量反馈**（用户评分 → 影响后续检索权重）             |

### 2.2 六大创新点详解

#### 创新点一：知识图谱增强检索（KG-RAG）

传统 RAG 仅依赖向量相似度检索，存在「语义相近但逻辑无关」的误召回问题。本方案在 Neo4j 中构建知识图谱，实现：

- **实体抽取：** 文档入库时自动抽取关键实体（人物、概念、技术、时间等）
- **关系发现：** 基于共现分析和 LLM 推理，自动建立实体间关系
- **图增强检索：** 向量检索召回 Top-K 文档后，通过图谱扩展关联实体，进行二阶段推理检索
- **子图可视化：** 前端展示检索到的知识子图，用户可直观看到知识关联

```
检索流程：用户问题 → 实体识别 → 向量检索(Top-20) → 图谱扩展(关联实体) → 重排序 → Top-5 上下文
```

#### 创新点二：自适应语义分块（Semantic-Aware Chunking）

固定大小分块（如 512 token）会破坏文档的语义完整性。本方案实现三级自适应分块：

| 层级     | 策略             | 说明                  |
| ------ | -------------- | ------------------- |
| L1 文档级 | 按一级标题拆分        | 保证大章节完整性            |
| L2 段落级 | 按二级标题 + 段落边界拆分 | 保证小节语义完整            |
| L3 句子级 | 对过长段落做句子边界拆分   | 兜底策略，确保不超过 token 上限 |

每个 chunk 携带层级元信息（`h1 → h2 → h3`），检索时可利用层级关系进行上下文扩展。

#### 创新点三：知识可信度溯源（Provenance Tracking）

每个回答附带完整的知识来源追溯链：

```
回答 → 检索到的文档块 → 所属文档 → 文档版本 → 上传时间 → 上传者
```

评分维度：

| 维度    | 权重  | 说明               |
| ----- | --- | ---------------- |
| 来源匹配度 | 40% | 检索相似度分数          |
| 文档新鲜度 | 20% | 基于时间的衰减因子        |
| 来源多样性 | 20% | 信息来源是否来自多个独立文档   |
| 历史验证度 | 20% | 该知识来源历史上被用户采纳的频率 |

#### 创新点四：增量学习与知识沉淀

对话过程中，AI 可能产生有价值的「新知」（如用户纠正、补充说明、总结归纳）。系统自动识别并建议沉淀：

```
对话结束 → LLM 提取可沉淀知识点 → 生成知识卡片 → 用户审核 → 入库（更新向量库 + 图谱）
```

#### 创新点五：多策略混合检索

结合三种检索策略，支持动态权重调整：

| 策略    | 引擎                   | 优势        | 权重(默认) |
| ----- | -------------------- | --------- | ------ |
| 关键词检索 | Elasticsearch BM25   | 精确匹配、专有名词 | 30%    |
| 语义检索  | pgvector/Milvus 向量检索 | 语义理解、模糊匹配 | 50%    |
| 图谱推理  | Neo4j 图遍历            | 关系推理、知识扩展 | 20%    |

最终得分 = BM25得分 × 0.3 + 向量得分 × 0.5 + 图谱得分 × 0.2

#### 创新点六：主动式知识发现

系统分析用户提问历史，基于知识图谱识别「知识盲区」：

- **盲区检测：** 用户问到的主题在图谱中关联稀疏，说明该领域知识覆盖不足
- **主动推荐：** 「您最近关注了 X 主题，但关于 X 的 Y 方面知识库尚未覆盖，是否上传相关资料？」
- **学习路径：** 基于已覆盖知识，推荐学习路径中缺失的前置/后续知识

***

## 3. 系统架构设计

### 3.0 架构分期策略

> 本方案基础设施较多（6 个存储组件），建议分期引入以控制复杂度：

| 阶段      | 必选组件                           | 说明               |
| ------- | ------------------------------ | ---------------- |
| 一期（MVP） | PostgreSQL + pgvector + 本地文件系统 | 核心 RAG 问答可用      |
| 二期      | + Redis                        | 分布式会话缓存          |
| 三期（可选）  | + Elasticsearch                | 数据量破万级文档时引入 BM25 |
| 三期（可选）  | + Neo4j                        | 需要图谱推理时引入        |

> **个人项目建议：** 一期即可覆盖 90% 使用场景。Neo4j 和 ES 可在确认需求后再引入。

### 3.1 整体架构图

```
┌──────────────────────────────────────────────────────────────────┐
│                         前端 (React + Ant Design)                   │
│   ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────────┐    │
│   │ 对话界面  │ │ 知识库管理│ │ 图谱可视化│ │  知识沉淀审核     │    │
│   └──────────┘ └──────────┘ └──────────┘ └──────────────────┘    │
└──────────────────────────────┬───────────────────────────────────┘
                               │ HTTP/SSE (Streaming)
┌──────────────────────────────┴───────────────────────────────────┐
│                    Spring Boot 3.x + Spring AI 1.x                │
│                                                                   │
│  ┌───────────────────────────────────────────────────────────┐   │
│  │                      API Layer (Controller)                 │   │
│  │  ChatController │ KBController │ GraphController │ ...     │   │
│  └───────────────────────────────────────────────────────────┘   │
│                               │                                   │
│  ┌───────────────────────────────────────────────────────────┐   │
│  │                    Service Layer                            │   │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐       │   │
│  │  │ ChatService  │ │  KBService   │ │GraphService  │       │   │
│  │  │  (RAG对话)   │ │ (知识库管理) │ │ (图谱服务)   │       │   │
│  │  └──────────────┘ └──────────────┘ └──────────────┘       │   │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐       │   │
│  │  │ChunkService  │ │RankService   │ │LearnService  │       │   │
│  │  │ (自适应分块) │ │ (混合检索)   │ │ (增量学习)   │       │   │
│  │  └──────────────┘ └──────────────┘ └──────────────┘       │   │
│  └───────────────────────────────────────────────────────────┘   │
│                               │                                   │
│  ┌───────────────────────────────────────────────────────────┐   │
│  │                    Spring AI Advisor Chain                   │   │
│  │  QuestionAnswerAdvisor → ProvenanceAdvisor → RerankAdvisor  │   │
│  └───────────────────────────────────────────────────────────┘   │
│                               │                                   │
│  ┌───────────────────────────────────────────────────────────┐   │
│  │                    基础设施层                                │   │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐     │   │
│  │  │PostgreSQL│ │ pgvector │ │  Neo4j   │ │   Redis   │     │   │
│  │  │(业务数据)│ │(向量存储)│ │(知识图谱)│ │ (缓存)    │     │   │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘     │   │
│  │  ┌──────────┐ ┌──────────────────────┐                    │   │
│  │  │ MinIO    │ │ Elasticsearch        │                    │   │
│  │  │(文件存储)│ │ (BM25关键词检索)     │                    │   │
│  │  └──────────┘ └──────────────────────┘                    │   │
│  └───────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────┘
```

### 3.2 核心流程

#### RAG 问答流程（增强版）

```
用户提问
    │
    ▼
┌─────────────┐
│ 1. 查询分析  │ ← LLM 提取意图 + 关键实体
└──────┬──────┘
       │
       ▼
┌─────────────────────────────┐
│ 2. 多策略混合检索            │
│  ┌──────┐ ┌──────┐ ┌──────┐ │
│  │BM25  │ │向量  │ │图谱  │ │
│  │检索  │ │检索  │ │推理  │ │
│  └──┬───┘ └──┬───┘ └──┬───┘ │
│     └────────┼────────┘     │
│              ▼              │
│        加权融合排序          │
└──────────────┬──────────────┘
               │
               ▼
┌─────────────┐
│ 3. 上下文组装│ ← 融入 chunk 层级元信息 + 知识来源追溯
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ 4. LLM 生成  │ ← 增强 Prompt（含上下文 + 溯源要求）
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ 5. 后处理    │ ← 可信度评分 + 来源标注 + 知识沉淀建议
└──────┬──────┘
       │
       ▼
  返回给用户（含引用来源）
```

#### 文档入库流程（ETL 增强版）

```
文档上传
    │
    ▼
┌──────────────────┐
│ 1. 格式解析       │ ← PDF / Markdown / Word / HTML
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ 2. 自适应语义分块  │ ← L1→L2→L3 三级分块
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ 3. 实体关系抽取    │ ← LLM 提取实体 + 关系
└────────┬─────────┘
         │
    ┌────┴────┐
    ▼         ▼
┌───────┐ ┌───────┐
│向量存储│ │图谱存储│
│pgvector│ │Neo4j  │
└───────┘ └───────┘
    │         │
    ▼         ▼
┌──────────────────┐
│ 4. ES 索引        │ ← BM25 全文索引
└────────┬─────────┘
         │
         ▼
    入库完成
```

***

## 4. 技术选型

### 4.1 后端技术栈

| 技术              | 版本         | 用途                               | Maven Artifact                               |
| --------------- | ---------- | -------------------------------- | -------------------------------------------- |
| Java            | 17+        | 开发语言                             | -                                            |
| Spring Boot     | 3.4.x      | 应用框架                             | spring-boot-starter-parent                   |
| Spring AI       | 1.0.0 (GA) | AI 集成框架                          | spring-ai-bom (BOM)                          |
| - Chat 模型       | -          | 对话能力                             | spring-ai-openai                             |
| - Embedding 模型  | -          | 向量化                              | spring-ai-openai（OpenAI兼容）                   |
| - pgvector      | -          | 向量存储                             | spring-ai-starter-vector-store-pgvector      |
| PostgreSQL      | 16         | 业务数据存储                           | postgresql                                   |
| Neo4j           | 5.x        | 知识图谱存储（二期可选）                     | spring-boot-starter-neo4j                    |
| Elasticsearch   | 8.x        | BM25 关键词检索（三期可选，一期用 PG tsvector） | spring-ai-starter-vector-store-elasticsearch |
| Redis           | 7.x        | 会话缓存 + 对话记忆（二期可选）                | spring-boot-starter-data-redis               |
| MinIO           | 最新稳定版      | 文件对象存储                           | minio                                        |
| Spring Data JPA | -          | ORM 框架                           | spring-boot-starter-data-jpa                 |
| Lombok          | -          | 代码简化                             | lombok                                       |
| MapStruct       | -          | 对象转换                             | mapstruct                                    |

### 4.2 前端技术栈

| 技术              | 用途          |
| --------------- | ----------- |
| React 18        | UI 框架       |
| Ant Design 5    | 组件库         |
| @antv/g6        | 知识图谱可视化     |
| react-markdown  | Markdown 渲染 |
| SSE EventSource | 流式对话        |

### 4.3 AI 模型选型建议

| 模型类型         | 推荐方案                             | 备选方案                      |
| ------------ | -------------------------------- | ------------------------- |
| Chat 模型      | 阿里通义千问 / DeepSeek                | OpenAI GPT-4o / 本地 Ollama |
| Embedding 模型 | text-embedding-v3 / bge-large-zh | m3e-base                  |
| 实体抽取模型       | 复用 Chat 模型 + Prompt              | 专用 NER 模型                 |

***

## 5. 核心模块详细设计

### 5.1 自适应语义分块模块（ChunkService）

#### 设计思路

传统 RAG 分块采用固定 token 大小切分，容易在句子中间截断，破坏语义。本模块实现三级分块策略。

#### 分块算法

```
输入：解析后的文档结构树（标题层级 + 段落列表）
输出：带层级元信息的 Chunk 列表

步骤：
1. 按一级标题将文档切分为 L1 Chunk
2. 对超过 maxTokens(1024) 的 L1 Chunk，按二级标题切分为 L2 Chunk
3. 对超过 maxTokens(1024) 的 L2 Chunk，按段落边界切分
4. 对超过 maxTokens(1024) 的段落，按句子边界切分（L3 兜底）
5. 每个 Chunk 记录层级路径，如：h1标题 > h2标题 > h3标题
6. 相邻 Chunk 保留 overlap（前一个 Chunk 的最后 2 句）
```

#### Chunk 元数据结构

```json
{
  "chunkId": "uuid",
  "documentId": "doc-001",
  "content": "这是文档内容...",
  "hierarchyPath": ["第一章 概述", "1.1 背景", "1.1.1 技术选型"],
  "hierarchyLevel": 3,
  "chunkIndex": 5,
  "parentChunkId": "uuid-of-l2-chunk",
  "tokenCount": 512,
  "entities": ["Spring AI", "RAG", "pgvector"],
  "metadata": {
    "source": "技术方案.pdf",
    "page": 12,
    "version": 1
  }
}
```

#### 检索时的上下文扩展

检索到某个 Chunk 后，自动拉取其父级 Chunk 和相邻 Chunk，形成更完整的上下文：

```
命中 Chunk(L3) → 扩展上级 Chunk(L2 摘要) → 扩展同级相邻 Chunk
```

### 5.2 多策略混合检索模块（RankService）

#### 架构设计

```
                    ┌─────────────────┐
                    │   Query Router   │ ← 分析查询类型，动态调权
                    └────────┬────────┘
                             │
          ┌──────────────────┼──────────────────┐
          ▼                  ▼                  ▼
   ┌────────────┐    ┌────────────┐    ┌────────────┐
   │ BM25 检索引擎│    │ 向量检索引擎│    │ 图谱检索引擎│
   │ (tsvector) │    │ (pgvector) │    │ (Neo4j)    │
   │ 一期:PG内置 │    │            │    │ 二期可选   │
   └──────┬─────┘    └──────┬─────┘    └──────┬─────┘
          │                 │                 │
          │  Top-15         │  Top-20         │  Top-15
          ▼                 ▼                 ▼
   ┌─────────────────────────────────────────────────┐
   │              Reciprocal Rank Fusion              │ ← RRF 融合算法
   └─────────────────────┬───────────────────────────┘
                         │
                         ▼
   ┌─────────────────────────────────────────────────┐
   │              加权评分 Reranker                   │ ← 轻量级精排
   └─────────────────────┬───────────────────────────┘
                         │
                         ▼
                    Top-5 结果
```

#### RRF（Reciprocal Rank Fusion）融合公式

```
RRF_score(d) = Σ(k=1 to N) 1 / (k + rank_k(d))

其中：
- d: 文档
- N: 检索策略数量（3）
- k: 平滑常数（默认 60）
- rank_k(d): 文档 d 在第 k 个检索策略中的排名
```

#### 动态权重调整策略

根据查询类型自动调整各检索引擎权重：

| 查询类型 | 判断条件          | BM25权重 | 向量权重 | 图谱权重 |
| ---- | ------------- | ------ | ---- | ---- |
| 精确查询 | 含专有名词/数字/代码   | 50%    | 30%  | 20%  |
| 概念查询 | 含「是什么」「定义」    | 20%    | 50%  | 30%  |
| 关系查询 | 含「关系」「关联」「依赖」 | 10%    | 30%  | 60%  |
| 综合查询 | 默认            | 30%    | 50%  | 20%  |

### 5.3 知识图谱模块（GraphService）

#### 图谱 Schema 设计

```
节点类型：
┌──────────┐    ┌──────────────┐    ┌──────────────────┐
│ Document │    │   Entity     │    │      Chunk       │
├──────────┤    ├──────────────┤    ├──────────────────┤
│ id       │    │ name         │    │ id               │
│ title    │    │ type         │    │ content          │
│ type     │    │ description  │    │ docId            │
│ version  │    │ aliases      │    │ level            │
│ createdAt│    │ createdAt    │    │ hierarchyPath    │
│          │    │              │    │ pgVectorChunkId  │ ← 关联 PG 中 chunk
└──────────┘    └──────────────┘    └──────────────────┘

关系类型：
(Document)-[:CONTAINS]->(Chunk)
(Chunk)-[:MENTIONS]->(Entity)
(Entity)-[:RELATED_TO {type, weight}]->(Entity)
(Entity)-[:BELONGS_TO]->(Category)
(Chunk)-[:REFERENCES]->(Chunk)      ← 跨文档引用
```

#### 实体关系抽取 Prompt

```
你是一个知识图谱构建助手。请从以下文本中提取：
1. 实体：提取关键概念、人物、技术、工具、方法论等
2. 关系：识别实体之间的关系类型

关系类型限定为：
- DEPENDS_ON（依赖）
- PART_OF（组成部分）
- DERIVED_FROM（衍生自）
- CONTRASTS_WITH（对比）
- PREREQUISITE_OF（前置知识）
- APPLIES_TO（适用于）

输出 JSON 格式：
{
  "entities": [
    {"name": "RAG", "type": "TECHNIQUE", "description": "检索增强生成技术"}
  ],
  "relations": [
    {"from": "RAG", "to": "向量数据库", "type": "DEPENDS_ON", "description": "RAG依赖向量数据库存储文档向量"}
  ]
}

文本内容：
{chunk_content}
```

#### 图谱推理检索

```
输入：用户问题中提取的实体列表
输出：扩展后的关联实体及对应 Chunk

Cypher 查询示例：
MATCH (e:Entity {name: 'RAG'})
MATCH (e)-[r:RELATED_TO*1..2]-(related:Entity)
MATCH (c:Chunk)-[:MENTIONS]->(related)
RETURN e, r, related, c
LIMIT 20
```

### 5.4 可信度溯源模块（ProvenanceAdvisor）

#### 自定义 Spring AI Advisor（适配 Spring AI 1.0 GA 最新 API）

> **重要说明：** Spring AI 1.0 M8 起，`CallAroundAdvisor` 已被标记为 Deprecated，推荐使用 `CallAdvisor` + `StreamAdvisor` 接口。

```java
import org.springframework.ai.chat.client.advisor.api.*;

/**
 * 可信度溯源 Advisor
 * 在 RAG 问答响应中附加来源追溯和可信度评分
 * 同时实现 CallAdvisor（非流式）和 StreamAdvisor（流式）
 */
public class ProvenanceAdvisor implements CallAdvisor, StreamAdvisor {

    @Override
    public String getName() {
        return "ProvenanceAdvisor";
    }

    @Override
    public int getOrder() {
        return 1;
    }

    // ==================== 非流式场景 ====================

    @Override
    public ChatClientResponse adviseCall(
            ChatClientRequest chatClientRequest,
            CallAdvisorChain callAdvisorChain) {

        // 1. 调用链获取原始响应
        ChatClientResponse response = callAdvisorChain.nextCall(chatClientRequest);

        // 2. 增强响应（附加来源追溯）
        return enhanceResponse(response);
    }

    // ==================== 流式场景 ====================

    @Override
    public Flux<ChatClientResponse> adviseStream(
            ChatClientRequest chatClientRequest,
            StreamAdvisorChain streamAdvisorChain) {

        Flux<ChatClientResponse> responses = streamAdvisorChain.nextStream(chatClientRequest);

        // 流式场景下，使用 MessageAggregator 聚合完整响应后再增强
        return new ChatClientMessageAggregator()
            .aggregateChatClientResponse(responses, this::enhanceResponse);
    }

    // ==================== 通用增强逻辑 ====================

    private ChatClientResponse enhanceResponse(ChatClientResponse response) {
        // 从响应元数据中获取检索到的文档
        List<Document> retrievedDocs = Optional.ofNullable(
                response.context().get(AbstractChatVectorStoreAdvisor.RETRIEVED_DOCUMENTS))
            .map(obj -> (List<Document>) obj)
            .orElse(Collections.emptyList());

        if (retrievedDocs.isEmpty()) {
            return response;
        }

        // 计算可信度评分
        double score = calculateCredibilityScore(retrievedDocs);

        // 构建来源追溯信息
        String provenance = buildProvenanceMarkdown(retrievedDocs, score);

        // 构建增强后的内容
        String originalContent = response.chatCompletion().getOutput().getText();
        String enhancedContent = originalContent + "\n\n---\n" + provenance;

        // 返回新的响应（通过 mutate 修改 content）
        return response.mutate()
            .chatCompletion(response.chatCompletion().mutate()
                .output(new AssistantMessage(enhancedContent))
                .build())
            .build();
    }

    private double calculateCredibilityScore(List<Document> docs) {
        double similarityScore = calculateAvgSimilarity(docs);      // 40%
        double freshnessScore = calculateFreshness(docs);           // 20%
        double diversityScore = calculateSourceDiversity(docs);     // 20%
        double historyScore = calculateHistoricalAccuracy(docs);    // 20%

        return similarityScore * 0.4 + freshnessScore * 0.2
             + diversityScore * 0.2 + historyScore * 0.2;
    }
}
```

#### 使用方式（ChatClient 链式调用）

```java
ChatResponse response = chatClient
    .prompt()
    .user(message)
    .advisors(new QuestionAnswerAdvisor(vectorStore))
    .advisors(new ProvenanceAdvisor())
    .call()
    .chatResponse();
```

### 5.5 增量学习模块（LearnService）

#### 知识沉淀流程

```
对话结束
    │
    ▼
┌─────────────────────────┐
│ 1. 对话摘要提取           │ ← LLM 总结对话中涉及的知识点
└───────────┬─────────────┘
            │
            ▼
┌─────────────────────────┐
│ 2. 新知识识别             │ ← 与现有知识库对比，识别增量知识
└───────────┬─────────────┘
            │
            ▼
┌─────────────────────────┐
│ 3. 知识卡片生成           │ ← 生成结构化知识卡片（标题、内容、标签、来源）
└───────────┬─────────────┘
            │
            ▼
┌─────────────────────────┐
│ 4. 用户审核               │ ← 前端展示待审核知识卡片，用户确认/修改/拒绝
└───────────┬─────────────┘
            │ (审核通过)
            ▼
┌─────────────────────────┐
│ 5. 入库                   │ ← 写入向量库 + 更新知识图谱 + ES 索引
└─────────────────────────┘
```

#### 知识卡片数据结构

```json
{
  "cardId": "uuid",
  "title": "RAG 中 RRF 融合算法的参数调优",
  "content": "RRF 融合算法中的 k 值（平滑常数）默认取 60...",
  "tags": ["RAG", "RRF", "检索融合"],
  "sourceConversationId": "conv-001",
  "sourceDocuments": ["doc-001", "doc-005"],
  "status": "PENDING_REVIEW",
  "createdAt": "2026-05-30T10:00:00Z"
}
```

### 5.6 主动知识发现模块（DiscoveryService）

#### 知识盲区检测算法

```java
public List<KnowledgeGap> detectKnowledgeGaps(String userId) {
    // 1. 获取用户最近 N 次提问主题
    List<String> queriedTopics = getUserQueryTopics(userId, 30);

    List<KnowledgeGap> gaps = new ArrayList<>();
    for (String topic : queriedTopics) {
        double density = calculateGraphCoverage(topic);
        if (density < THRESHOLD) {
            List<String> missingSubtopics = findMissingSubtopics(topic);
            gaps.add(new KnowledgeGap(topic, density, missingSubtopics));
        }
    }
    return gaps;
}

/**
 * 计算主题在知识图谱中的覆盖密度
 *
 * 步骤：
 * 1. 使用 LLM 提取 topic 关键词/实体
 * 2. 在图谱中查找这些实体及其关联节点
 * 3. 覆盖密度 = 图谱中已覆盖的关联实体数 / 该主题的标准知识体系中的实体总数
 */
private double calculateGraphCoverage(String topic) {
    // Step 1: LLM 提取 topic 关联的标准实体集合
    List<String> expectedEntities = llm.extractStandardEntities(topic);

    // Step 2: 查询图谱中已存在的实体
    Set<String> existingEntities = entityRepository.findByNames(expectedEntities)
        .stream().map(Entity::getName).collect(Collectors.toSet());

    // Step 3: 计算覆盖密度
    if (expectedEntities.isEmpty()) return 1.0;
    return (double) existingEntities.size() / expectedEntities.size();
}
```

***

## 6. 项目结构设计

> **设计原则：** 基于 Spring AI 1.0 GA 官方示例 [habuma/spring-ai-examples](https://github.com/habuma/spring-ai-examples)、
> [Spring AI Alibaba Best Practices](https://github.com/hllqkb/Spring-AI-Ailibaba-Best-Practices)
> 和 2025 年企业级 RAG 项目最佳实践，采用 **按功能垂直划分 + 内部技术分层** 的混合架构。

### 6.1 整体项目结构（Maven 单模块，个人项目简化）

```
knowledge-forge-ai/
├── pom.xml                              # Maven 项目配置
├── docker-compose.yml                   # 开发环境容器编排
├── Dockerfile                           # 生产环境容器构建
├── .env                                 # 环境变量（不提交 Git）
├── .gitignore
│
├── src/
│   ├── main/
│   │   ├── java/com/knowledgeforge/
│   │   │   ├── KnowledgeForgeApplication.java    # Spring Boot 启动类
│   │   │   │
│   │   │   ├── config/                           # 全局配置
│   │   │   │   ├── AiConfig.java                 # AI 模型配置（ChatClient、EmbeddingModel）
│   │   │   │   ├── PgVectorStoreConfig.java      # pgvector 向量存储配置
│   │   │   │   ├── Neo4jConfig.java              # Neo4j 图谱配置（二期）
│   │   │   │   ├── MinioConfig.java              # MinIO 文件存储配置
│   │   │   │   ├── RedisConfig.java              # Redis 会话缓存配置（二期）
│   │   │   │   └── WebMvcConfig.java             # Web MVC 配置（CORS、拦截器）
│   │   │   │
│   │   │   ├── chat/                             # 对话功能模块
│   │   │   │   ├── controller/
│   │   │   │   │   ├── ChatController.java       # RAG 对话接口（SSE 流式）
│   │   │   │   │   └── ConversationController.java # 会话管理接口
│   │   │   │   ├── service/
│   │   │   │   │   ├── ChatService.java          # 对话核心业务逻辑
│   │   │   │   │   └── ConversationService.java  # 会话历史管理
│   │   │   │   ├── dto/
│   │   │   │   │   ├── ChatRequest.java          # 对话请求 DTO
│   │   │   │   │   ├── ChatResponse.java         # 对话响应 DTO
│   │   │   │   │   └── MessageDTO.java           # 消息 DTO
│   │   │   │   └── advisor/
│   │   │   │       └── ProvenanceAdvisor.java    # 可信度溯源 Advisor
│   │   │   │
│   │   │   ├── knowledge/                        # 知识库管理模块
│   │   │   │   ├── controller/
│   │   │   │   │   ├── KnowledgeBaseController.java # 知识库 CRUD
│   │   │   │   │   └── DocumentController.java   # 文档上传/管理
│   │   │   │   ├── service/
│   │   │   │   │   ├── KnowledgeBaseService.java # 知识库业务逻辑
│   │   │   │   │   └── DocumentService.java      # 文档处理逻辑
│   │   │   │   ├── dto/
│   │   │   │   │   ├── KnowledgeBaseDTO.java
│   │   │   │   │   ├── DocumentDTO.java
│   │   │   │   │   └── DocumentUploadRequest.java
│   │   │   │   ├── entity/
│   │   │   │   │   ├── KnowledgeBase.java        # 知识库实体
│   │   │   │   │   └── Document.java             # 文档实体
│   │   │   │   └── repository/
│   │   │   │       ├── KnowledgeBaseRepository.java
│   │   │   │       └── DocumentRepository.java
│   │   │   │
│   │   │   ├── retrieval/                        # 检索模块（RAG 核心）
│   │   │   │   ├── service/
│   │   │   │   │   ├── ChunkService.java         # 自适应语义分块
│   │   │   │   │   ├── RankService.java          # 多策略混合检索（RRF 融合）
│   │   │   │   │   └── Bm25SearchService.java    # tsvector BM25 关键词检索
│   │   │   │   └── dto/
│   │   │   │       └── SearchRequest.java        # 检索请求
│   │   │   │
│   │   │   ├── graph/                            # 知识图谱模块（二期）
│   │   │   │   ├── controller/
│   │   │   │   │   └── GraphController.java      # 图谱查询/可视化接口
│   │   │   │   ├── service/
│   │   │   │   │   ├── GraphService.java         # 图谱构建与查询
│   │   │   │   │   └── EntityExtractionService.java # LLM 实体关系抽取
│   │   │   │   ├── entity/
│   │   │   │   │   └── EntityNode.java           # Neo4j 节点映射
│   │   │   │   └── repository/
│   │   │   │       └── EntityRepository.java     # Spring Data Neo4j
│   │   │   │
│   │   │   ├── learning/                         # 增量学习模块（三期）
│   │   │   │   ├── controller/
│   │   │   │   │   └── KnowledgeCardController.java # 知识卡片审核接口
│   │   │   │   ├── service/
│   │   │   │   │   ├── LearnService.java         # 知识沉淀流程
│   │   │   │   │   └── KnowledgeCardService.java # 知识卡片管理
│   │   │   │   ├── entity/
│   │   │   │   │   └── KnowledgeCard.java        # 知识卡片实体
│   │   │   │   └── repository/
│   │   │   │       └── KnowledgeCardRepository.java
│   │   │   │
│   │   │   ├── discovery/                        # 主动发现模块（三期）
│   │   │   │   ├── controller/
│   │   │   │   │   └── DiscoveryController.java  # 知识盲区/推荐接口
│   │   │   │   └── service/
│   │   │   │       └── DiscoveryService.java     # 盲区检测算法
│   │   │   │
│   │   │   ├── shared/                           # 共享组件
│   │   │   │   ├── exception/
│   │   │   │   │   ├── GlobalExceptionHandler.java # 全局异常处理
│   │   │   │   │   ├── DocumentProcessException.java # 文档处理异常
│   │   │   │   │   └── SearchException.java      # 检索异常
│   │   │   │   ├── dto/
│   │   │   │   │   ├── ApiResponse.java          # 统一响应包装
│   │   │   │   │   └── PageResult.java           # 分页结果
│   │   │   │   ├── util/
│   │   │   │   │   ├── FileUtil.java             # 文件处理工具
│   │   │   │   │   ├── TokenUtil.java            # Token 计算工具
│   │   │   │   │   └── TextSplitUtil.java        # 文本分割工具
│   │   │   │   └── constant/
│   │   │   │       └── SystemConstants.java      # 系统常量
│   │   │   │
│   │   │   └── document/                         # 文档解析模块
│   │   │       ├── service/
│   │   │       │   ├── DocumentParserFactory.java # 解析器工厂
│   │   │       │   ├── PdfParser.java            # PDF 解析
│   │   │       │   ├── WordParser.java           # Word 解析
│   │   │       │   └── MarkdownParser.java       # Markdown 解析
│   │   │       └── dto/
│   │   │           └── ParsedDocument.java       # 解析后文档对象
│   │   │
│   │   └── resources/
│   │       ├── application.yml                   # 主配置文件
│   │       ├── application-dev.yml               # 开发环境配置
│   │       ├── application-prod.yml              # 生产环境配置
│   │       ├── db/
│   │       │   └── migration/                    # Flyway 数据库迁移脚本
│   │       │       ├── V1__init_schema.sql
│   │       │       ├── V2__add_bm25_index.sql
│   │       │       └── V3__add_graph_schema.sql  # 二期图谱表
│   │       ├── prompts/                          # Prompt 模板
│   │       │   ├── system-prompt.st              # 系统提示词
│   │       │   ├── entity-extraction.st          # 实体抽取 Prompt
│   │       │   └── knowledge-card.st             # 知识卡片生成 Prompt
│   │       └── static/                           # 静态资源（如有）
│   │
│   └── test/
│       └── java/com/knowledgeforge/
│           ├── chat/
│           │   └── ChatServiceTest.java
│           ├── retrieval/
│           │   └── RankServiceTest.java
│           └── KnowledgeForgeApplicationTests.java
│
├── frontend/                                    # React 前端项目（独立目录）
│   ├── package.json
│   ├── src/
│   │   ├── pages/
│   │   │   ├── ChatPage.tsx
│   │   │   ├── KnowledgeBasePage.tsx
│   │   │   ├── GraphPage.tsx
│   │   │   └── DiscoveryPage.tsx
│   │   ├── components/
│   │   ├── services/
│   │   └── App.tsx
│   └── ...
```

### 6.2 包结构组织原则

| 维度       | 策略          | 说明                                                                |
| -------- | ----------- | ----------------------------------------------------------------- |
| **一级组织** | **按功能垂直划分** | `chat/`、`knowledge/`、`retrieval/`、`graph/` 各自为独立功能域               |
| **二级组织** | **按技术分层**   | 每个功能模块内部有 `controller/`、`service/`、`dto/`、`entity/`、`repository/` |
| **全局组件** | **按类型集中**   | `config/`、`shared/`、`document/` 存放跨功能共享代码                         |

### 6.3 模块依赖关系

```
                     ┌─────────────┐
                     │  shared/    │ ← 无依赖（最底层）
                     └──────┬──────┘
                            │
       ┌────────────────────┼────────────────────┐
       │                    │                    │
┌──────┴──────┐    ┌────────┴───────┐   ┌───────┴───────┐
│ knowledge/  │    │  retrieval/    │   │  document/    │
│ (知识库)    │    │  (检索)        │   │  (文档解析)   │
└──────┬──────┘    └────────┬───────┘   └───────┬───────┘
       │                    │                    │
       └────────────────────┼────────────────────┘
                            │
                     ┌──────┴──────┐
                     │   chat/     │ ← 依赖 knowledge + retrieval
                     └──────┬──────┘
                            │
       ┌────────────────────┼────────────────────┐
       │                    │                    │
┌──────┴──────┐    ┌────────┴───────┐   ┌───────┴───────┐
│  graph/     │    │   learning/    │   │  discovery/   │
│  (二期)     │    │   (三期)       │   │  (三期)       │
└─────────────┘    └────────────────┘   └───────────────┘
```

**依赖方向规则：**

- 底层模块（shared、document）**不能依赖**上层模块
- 上层模块**只能依赖**下层模块，禁止横向跨模块直接调用
- 跨模块通信通过 **事件（Spring ApplicationEvent）** 或 **公共接口** 实现

### 6.4 资源文件组织

| 目录                                    | 用途                      | 示例                    |
| ------------------------------------- | ----------------------- | --------------------- |
| `resources/prompts/`                  | LLM Prompt 模板（`.st` 文件） | `system-prompt.st`    |
| `resources/db/migration/`             | Flyway 数据库版本迁移脚本        | `V1__init_schema.sql` |
| `resources/application-{profile}.yml` | 环境配置                    | `application-dev.yml` |

### 6.5 Maven 依赖结构（pom.xml 核心配置）

```xml
<project>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.0</version>
    </parent>

    <properties>
        <java.version>17</java.version>
        <spring-ai.version>1.0.0</spring-ai.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>1.0.0</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <!-- Spring Boot 基础 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <!-- Spring AI 核心 -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-openai</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-vector-store-pgvector</artifactId>
        </dependency>

        <!-- 数据库 -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>

        <!-- 文档解析 -->
        <dependency>
            <groupId>org.apache.pdfbox</groupId>
            <artifactId>pdfbox</artifactId>
            <version>3.0.1</version>
        </dependency>
        <dependency>
            <groupId>org.apache.poi</groupId>
            <artifactId>poi-ooxml</artifactId>
            <version>5.2.5</version>
        </dependency>

        <!-- 文件存储 -->
        <dependency>
            <groupId>io.minio</groupId>
            <artifactId>minio</artifactId>
            <version>8.5.7</version>
        </dependency>

        <!-- 工具 -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.mapstruct</groupId>
            <artifactId>mapstruct</artifactId>
            <version>1.5.5.Final</version>
        </dependency>

        <!-- 测试 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

***

## 7. 数据库设计

> **重要说明：** Spring AI 1.0 的 `PgVectorStore` 默认使用 `vector_store` 作为表名。
> 本项目采用 **自定义表（document\_chunk）** 存储向量和业务数据，原因：
>
> 1. Spring AI 默认表无 `hierarchy_path`、`document_id` 等业务字段
> 2. 自定义表支持知识溯源和层级扩展
> 3. 需要在 Bean 配置中指定 `vectorTableName = "document_chunk"`
>
> 配置方式：
>
> ```java
> @Bean
> public PgVectorStore pgVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
>     return PgVectorStore.builder(jdbcTemplate, embeddingModel)  // 使用 JdbcTemplate 而非 DataSource
>         .initializeSchema(false)  // 使用手动建表
>         .vectorTableName("document_chunk")
>         .dimensions(1536)
>         .distanceType(PgDistanceType.COSINE_DISTANCE)
>         .indexType(PgIndexType.HNSW)
>         .build();
> }
> ```

### 6.1 PostgreSQL 核心表

#### 知识库表（knowledge\_base）

```sql
CREATE TABLE knowledge_base (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    icon        VARCHAR(50),
    deleted     BOOLEAN DEFAULT FALSE,
    created_at  TIMESTAMP DEFAULT NOW(),
    updated_at  TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_kb_deleted ON knowledge_base(deleted);
```

#### 文档表（document）

```sql
CREATE TABLE document (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kb_id            UUID REFERENCES knowledge_base(id),
    title            VARCHAR(500) NOT NULL,
    file_type        VARCHAR(20) NOT NULL,      -- PDF, MARKDOWN, WORD, HTML
    file_path        VARCHAR(1000),              -- MinIO 路径
    file_size        BIGINT,
    chunk_count      INTEGER DEFAULT 0,
    status           VARCHAR(20) DEFAULT 'PROCESSING', -- PROCESSING, READY, FAILED
    error_message    TEXT,                       -- 记录处理失败原因
    version          INTEGER DEFAULT 1,
    deleted          BOOLEAN DEFAULT FALSE,
    created_at       TIMESTAMP DEFAULT NOW(),
    updated_at       TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_document_kb_id ON document(kb_id);
CREATE INDEX idx_document_deleted ON document(deleted);
```

#### 文档块表（document\_chunk）

```sql
CREATE TABLE document_chunk (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id      UUID REFERENCES document(id) ON DELETE CASCADE,
    content          TEXT NOT NULL,
    hierarchy_path   JSONB DEFAULT '[]',          -- JSONB 数组：["第一章", "1.1 概述"]
    hierarchy_level  INTEGER DEFAULT 1,
    chunk_index      INTEGER NOT NULL,
    parent_chunk_id  UUID,
    token_count      INTEGER,
    embedding        vector(1536),                -- pgvector 向量字段
    metadata         JSONB DEFAULT '{}',
    created_at       TIMESTAMP DEFAULT NOW()
);

-- HNSW 向量索引（适合大数据量，性能优于 IVFFlat）
CREATE INDEX idx_chunk_embedding ON document_chunk
    USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 200);

-- BM25 全文检索索引（一期使用 PostgreSQL 内置 tsvector，无需 ES）
ALTER TABLE document_chunk ADD COLUMN content_tsvector tsvector
    GENERATED ALWAYS AS (to_tsvector('jiebacfg', content)) STORED;
CREATE INDEX idx_chunk_bm25 ON document_chunk USING GIN (content_tsvector);

CREATE INDEX idx_chunk_document_id ON document_chunk(document_id);
```

#### 对话表（conversation）

```sql
CREATE TABLE conversation (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     VARCHAR(100) NOT NULL,
    title       VARCHAR(500),
    deleted     BOOLEAN DEFAULT FALSE,
    created_at  TIMESTAMP DEFAULT NOW(),
    updated_at  TIMESTAMP DEFAULT NOW()
);

-- 对话与知识库的关联表（替代 UUID[] 数组）
CREATE TABLE conversation_kb (
    conversation_id UUID REFERENCES conversation(id) ON DELETE CASCADE,
    kb_id           UUID REFERENCES knowledge_base(id) ON DELETE CASCADE,
    PRIMARY KEY (conversation_id, kb_id)
);
```

#### 对话消息表（chat\_message）

```sql
CREATE TABLE chat_message (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID REFERENCES conversation(id) ON DELETE CASCADE,
    role            VARCHAR(20) NOT NULL CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    content         TEXT NOT NULL,
    sources         JSONB,                      -- 引用的文档来源
    credibility_score DOUBLE PRECISION,         -- 可信度评分
    feedback        VARCHAR(20),                -- POSITIVE, NEGATIVE, NONE
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_message_conversation_id ON chat_message(conversation_id);
```

#### 知识卡片表（knowledge\_card）

```sql
CREATE TABLE knowledge_card (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title                   VARCHAR(500) NOT NULL,
    content                 TEXT NOT NULL,
    tags                    TEXT[],
    source_conversation_id  UUID,
    source_document_ids     UUID[],
    status                  VARCHAR(20) DEFAULT 'PENDING_REVIEW', -- PENDING_REVIEW, APPROVED, REJECTED
    created_at              TIMESTAMP DEFAULT NOW(),
    reviewed_at             TIMESTAMP
);

CREATE INDEX idx_card_conversation_id ON knowledge_card(source_conversation_id);
```

### 6.2 Neo4j 图谱模型

```cypher
-- 创建约束（Neo4j 5.x 语法，小写蛇形命名）
CREATE CONSTRAINT entity_name_unique IF NOT EXISTS
    FOR (e:Entity) REQUIRE e.name IS UNIQUE;

CREATE CONSTRAINT document_id_unique IF NOT EXISTS
    FOR (d:Document) REQUIRE d.id IS UNIQUE;

CREATE CONSTRAINT chunk_id_unique IF NOT EXISTS
    FOR (c:Chunk) REQUIRE c.id IS UNIQUE;

-- 创建索引
CREATE INDEX entity_type_idx IF NOT EXISTS
    FOR (e:Entity) ON (e.type);

CREATE FULLTEXT INDEX entity_name_fulltext_idx IF NOT EXISTS
    FOR (e:Entity) ON EACH [e.name];
```

### 6.3 Elasticsearch 索引映射

```json
{
  "mappings": {
    "properties": {
      "chunk_id": { "type": "keyword" },
      "document_id": { "type": "keyword" },
      "kb_id": { "type": "keyword" },
      "content": {
        "type": "text",
        "analyzer": "ik_max_word",
        "search_analyzer": "ik_smart"
      },
      "hierarchy_path": {
        "type": "text",
        "fields": {
          "keyword": { "type": "keyword" }
        }
      },
      "entities": { "type": "keyword" },
      "created_at": { "type": "date" }
    }
  }
}
```

***

## 7. API 接口设计

### 7.1 对话接口

| 方法     | 路径                                    | 说明               |
| ------ | ------------------------------------- | ---------------- |
| POST   | `/api/v1/chat/rag`                    | RAG 增强对话（SSE 流式） |
| POST   | `/api/v1/chat/simple`                 | 普通对话（不含知识库）      |
| GET    | `/api/v1/conversations`               | 获取对话列表           |
| GET    | `/api/v1/conversations/{id}/messages` | 获取对话消息           |
| POST   | `/api/v1/messages/{id}/feedback`      | 提交答案反馈           |
| DELETE | `/api/v1/conversations/{id}`          | 删除对话             |

#### RAG 对话请求体

```json
{
  "message": "RAG 技术中如何选择合适的 Embedding 模型？",
  "conversationId": "conv-001",
  "kbIds": ["kb-001", "kb-002"],
  "searchStrategy": "HYBRID",
  "topK": 5,
  "stream": true
}
```

#### RAG 对话响应（SSE 事件流）

```
event: chunk
data: {"content": "选择 Embedding 模型时需要考虑...", "done": false}

event: source
data: {"sources": [{"docTitle": "RAG实践指南", "chunkIndex": 5, "score": 0.92}]}

event: credibility
data: {"score": 0.87, "breakdown": {"similarity": 0.92, "freshness": 0.85, ...}}

event: suggestions
data: {"knowledgeGaps": [...]}

event: done
data: {"messageId": "msg-001", "totalTokens": 1024}
```

### 7.2 知识库管理接口

| 方法     | 路径                                       | 说明              |
| ------ | ---------------------------------------- | --------------- |
| POST   | `/api/v1/knowledge-bases`                | 创建知识库           |
| GET    | `/api/v1/knowledge-bases`                | 获取知识库列表         |
| PUT    | `/api/v1/knowledge-bases/{id}`           | 更新知识库           |
| DELETE | `/api/v1/knowledge-bases/{id}`           | 删除知识库           |
| POST   | `/api/v1/knowledge-bases/{id}/documents` | 上传文档（multipart） |
| GET    | `/api/v1/knowledge-bases/{id}/documents` | 获取文档列表          |
| DELETE | `/api/v1/documents/{id}`                 | 删除文档            |
| GET    | `/api/v1/documents/{id}/chunks`          | 获取文档分块详情        |
| POST   | `/api/v1/documents/{id}/reprocess`       | 重新处理文档          |

### 7.3 知识图谱接口

| 方法  | 路径                              | 说明              |
| --- | ------------------------------- | --------------- |
| GET | `/api/v1/graph/entities`        | 获取实体列表（分页）      |
| GET | `/api/v1/graph/entities/{name}` | 获取实体详情及关联       |
| GET | `/api/v1/graph/subgraph`        | 获取指定实体的子图（可视化用） |
| GET | `/api/v1/graph/search`          | 图谱搜索            |

### 7.4 增量学习接口

| 方法   | 路径                                     | 说明        |
| ---- | -------------------------------------- | --------- |
| GET  | `/api/v1/knowledge-cards/pending`      | 获取待审核知识卡片 |
| POST | `/api/v1/knowledge-cards/{id}/approve` | 审核通过      |
| POST | `/api/v1/knowledge-cards/{id}/reject`  | 审核拒绝      |
| GET  | `/api/v1/knowledge-cards`              | 获取已入库知识卡片 |

### 7.5 主动发现接口

| 方法  | 路径                                  | 说明       |
| --- | ----------------------------------- | -------- |
| GET | `/api/v1/discovery/gaps`            | 获取知识盲区列表 |
| GET | `/api/v1/discovery/recommendations` | 获取知识推荐   |
| GET | `/api/v1/discovery/learning-path`   | 获取推荐学习路径 |

***

## 8. 前端页面规划

### 8.1 页面结构

```
├── /chat                    ← 对话主界面（核心页面）
│   ├── 对话列表（左侧栏）
│   ├── 对话区域（中间）
│   │   ├── 消息流（含来源引用标记）
│   │   ├── 可信度分数展示
│   │   └── 输入框（支持选择知识库、检索策略）
│   └── 上下文面板（右侧栏）
│       ├── 当前引用来源列表
│       ├── 相关知识推荐
│       └── 知识盲区提示
│
├── /knowledge-bases         ← 知识库管理
│   ├── 知识库列表
│   ├── 文档上传（拖拽+进度条）
│   ├── 文档列表（含处理状态）
│   └── 分块预览
│
├── /knowledge-graph         ← 知识图谱可视化
│   ├── 图谱画布（@antv/g6 力导向图）
│   ├── 实体搜索
│   └── 实体详情面板
│
├── /knowledge-cards         ← 知识沉淀审核
│   ├── 待审核列表
│   ├── 知识卡片详情（编辑/通过/拒绝）
│   └── 已入库知识库
│
└── /discovery               ← 知识发现
    ├── 知识盲区列表
    ├── 学习路径图
    └── 知识推荐卡片
```

***

## 9. 部署架构

### 9.1 Docker Compose 编排

```yaml
version: '3.8'
services:
  postgres:
    image: pgvector/pgvector:pg16
    environment:
      POSTGRES_DB: knowledgeforge
      POSTGRES_USER: kf_user
      POSTGRES_PASSWORD: ${PG_PASSWORD}
    ports:
      - "5432:5432"
    volumes:
      - pg_data:/var/lib/postgresql/data

  neo4j:
    image: neo4j:5-community
    environment:
      NEO4J_AUTH: neo4j/${NEO4J_PASSWORD}
    ports:
      - "7474:7474"
      - "7687:7687"
    volumes:
      - neo4j_data:/data

  elasticsearch:
    image: elasticsearch:8.12.0
    environment:
      discovery.type: single-node
      xpack.security.enabled: false
    ports:
      - "9200:9200"
    volumes:
      - es_data:/usr/share/elasticsearch/data

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  minio:
    image: minio/minio
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: ${MINIO_ACCESS_KEY}
      MINIO_ROOT_PASSWORD: ${MINIO_SECRET_KEY}
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - minio_data:/data

  app:
    build: .
    ports:
      - "8080:8080"
    depends_on:
      - postgres
      - neo4j
      - elasticsearch
      - redis
      - minio
    environment:
      SPRING_PROFILES_ACTIVE: docker

volumes:
  pg_data:
  neo4j_data:
  es_data:
  minio_data:
```

### 9.2 Spring AI 核心配置

```yaml
# 方案一：使用 DeepSeek（OpenAI 兼容 API）
spring:
  ai:
    openai:
      api-key: ${DEEPSEEK_API_KEY}
      base-url: https://api.deepseek.com
      chat:
        options:
          model: deepseek-chat
          temperature: 0.7
      embedding:
        options:
          model: text-embedding-3-small   # OpenAI 兼容的 Embedding 模型
    vectorstore:
      pgvector:
        initialize-schema: false          # 手动管理表结构，避免与 Spring AI 自动建表冲突
        index-type: hnsw
        distance-type: cosine_distance
        dimensions: 1536
        collection-name: document_chunk   # 指定自定义表名（底层映射为 vectorTableName）
```

> **数据库扩展要求：** PostgreSQL 需要启用以下扩展：
>
> ```sql
> CREATE EXTENSION IF NOT EXISTS vector;          -- pgvector 向量支持
> CREATE EXTENSION IF NOT EXISTS hstore;          -- Spring AI pgvector 需要
> CREATE EXTENSION IF NOT EXISTS "uuid-ossp";     -- UUID 生成
> CREATE EXTENSION IF NOT EXISTS zhparser;        -- 中文全文检索（jieba 分词）
> -- 或者使用 pg_jieba 扩展
> CREATE TEXT SEARCH CONFIGURATION jiebacfg (PARSER = zhparser);
> ```
>
> **中文分词说明：** PostgreSQL 内置的 tsvector 对英文支持良好，但对中文需要额外的分词扩展。
> 推荐使用 `zhparser`（基于 SCWS）或 `pg_jieba`（基于 Jieba）。
> Docker 镜像可预装：
>
> ```dockerfile
> FROM pgvector/pgvector:pg16
> RUN apt-get update && apt-get install -y \
>     postgresql-16-zhparser \
>     && rm -rf /var/lib/apt/lists/*
> ```

<br />

