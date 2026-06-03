package com.knowledgeforge.system.retrieval.service;

import com.knowledgeforge.core.entity.DocumentChunk;
import com.knowledgeforge.core.shared.constant.SystemConstants;
import com.knowledgeforge.system.graph.service.GraphService;
import com.knowledgeforge.system.knowledge.repository.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSearchService {

    private static final double RRF_K = 60.0;
    private static final double VECTOR_WEIGHT = 0.7;
    private static final double KEYWORD_WEIGHT = 0.3;

    private final RetrievalService retrievalService;
    private final KeywordSearchService keywordSearchService;
    private final GraphService graphService;
    private final DocumentChunkRepository chunkRepository;

    public List<Document> hybridSearch(String query, UUID kbId) {
        List<Document> vectorResults = retrievalService.retrieve(query, kbId);
        List<Document> keywordResults = keywordSearchService.search(query, kbId, SystemConstants.RETRIEVAL_MAX_RESULTS);

        List<Document> graphResults = graphEnhancedSearch(query, kbId);

        if (keywordResults.isEmpty() && graphResults.isEmpty()) {
            log.info("关键词和图谱检索均无结果，仅使用向量检索结果");
            return vectorResults;
        }

        List<Document> merged = rrfMerge(vectorResults, keywordResults, graphResults);
        log.info("混合检索: 向量={}条, 关键词={}条, 图谱={}条, 融合后={}条",
                vectorResults.size(), keywordResults.size(), graphResults.size(), merged.size());
        return merged;
    }

    public List<Document> hybridSearchMultipleKbs(String query, List<UUID> kbIds) {
        List<Document> vectorResults = retrievalService.retrieveMultipleKbs(query, kbIds);

        List<Document> keywordResults = new ArrayList<>();
        List<Document> graphResults = new ArrayList<>();
        for (UUID kbId : kbIds) {
            keywordResults.addAll(keywordSearchService.search(query, kbId, SystemConstants.RETRIEVAL_MAX_RESULTS));
            graphResults.addAll(graphEnhancedSearch(query, kbId));
        }
        keywordResults = keywordResults.stream()
                .sorted(Comparator.comparingDouble(
                        (Document d) -> ((Number) d.getMetadata().getOrDefault("keyword_score", 0.0)).doubleValue()
                ).reversed())
                .limit(SystemConstants.RETRIEVAL_MAX_RESULTS)
                .toList();
        graphResults = graphResults.stream()
                .sorted(Comparator.comparingDouble(
                        (Document d) -> ((Number) d.getMetadata().getOrDefault("graph_score", 0.0)).doubleValue()
                ).reversed())
                .limit(SystemConstants.RETRIEVAL_MAX_RESULTS)
                .toList();

        if (keywordResults.isEmpty() && graphResults.isEmpty()) {
            return vectorResults;
        }

        List<Document> merged = rrfMerge(vectorResults, keywordResults, graphResults);
        log.info("混合检索(多知识库): 向量={}条, 关键词={}条, 图谱={}条, 融合后={}条",
                vectorResults.size(), keywordResults.size(), graphResults.size(), merged.size());
        return merged;
    }

    private List<Document> rrfMerge(List<Document> vectorResults, List<Document> keywordResults, List<Document> graphResults) {
        Map<String, Double> rrfScores = new HashMap<>();
        Map<String, Document> docMap = new HashMap<>();

        for (int i = 0; i < vectorResults.size(); i++) {
            Document doc = vectorResults.get(i);
            String chunkId = (String) doc.getMetadata().get("chunk_id");
            if (chunkId == null) {
                chunkId = doc.getText().hashCode() + "_" + i;
            }
            double rrf = VECTOR_WEIGHT / (RRF_K + i + 1);
            rrfScores.put(chunkId, rrfScores.getOrDefault(chunkId, 0.0) + rrf);
            docMap.putIfAbsent(chunkId, doc);
        }

        double kwWeight = KEYWORD_WEIGHT;
        for (int i = 0; i < keywordResults.size(); i++) {
            Document doc = keywordResults.get(i);
            String chunkId = (String) doc.getMetadata().get("chunk_id");
            if (chunkId == null) {
                chunkId = doc.getText().hashCode() + "_kw_" + i;
            }
            double rrf = kwWeight / (RRF_K + i + 1);
            rrfScores.put(chunkId, rrfScores.getOrDefault(chunkId, 0.0) + rrf);
            docMap.putIfAbsent(chunkId, doc);
        }

        double graphWeight = 0.15;
        for (int i = 0; i < graphResults.size(); i++) {
            Document doc = graphResults.get(i);
            String chunkId = (String) doc.getMetadata().get("chunk_id");
            if (chunkId == null) {
                chunkId = doc.getText().hashCode() + "_gr_" + i;
            }
            double rrf = graphWeight / (RRF_K + i + 1);
            rrfScores.put(chunkId, rrfScores.getOrDefault(chunkId, 0.0) + rrf);
            docMap.putIfAbsent(chunkId, doc);
        }

        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(SystemConstants.RETRIEVAL_TOP_K)
                .map(entry -> {
                    Document doc = docMap.get(entry.getKey());
                    doc.getMetadata().put("hybrid_score", entry.getValue());
                    return doc;
                })
                .toList();
    }

    private List<Document> graphEnhancedSearch(String query, UUID kbId) {
        try {
            List<UUID> relatedChunkIds = graphService.findRelatedChunks(kbId, query);
            if (relatedChunkIds.isEmpty()) {
                return List.of();
            }

            List<DocumentChunk> chunks = chunkRepository.findAllById(relatedChunkIds);
            Map<UUID, String> contentMap = chunks.stream()
                    .collect(Collectors.toMap(DocumentChunk::getId, DocumentChunk::getContent));

            List<Document> docs = new ArrayList<>();
            for (int i = 0; i < relatedChunkIds.size(); i++) {
                UUID chunkId = relatedChunkIds.get(i);
                double score = 1.0 / (i + 1);

                String content = contentMap.getOrDefault(chunkId, "");

                Document doc = new Document(content);
                doc.getMetadata().put("chunk_id", chunkId.toString());
                doc.getMetadata().put("graph_score", score);
                doc.getMetadata().put("kb_id", kbId.toString());
                docs.add(doc);
            }
            return docs;
        } catch (Exception e) {
            log.warn("图谱检索失败: {}", e.getMessage());
            return List.of();
        }
    }
}