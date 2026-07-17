package com.knowledgeforge.system.retrieval.service;

import com.knowledgeforge.core.entity.DocumentChunk;
import com.knowledgeforge.core.shared.constant.SystemConstants;
import com.knowledgeforge.system.chat.observability.RagObservation;
import com.knowledgeforge.system.config.HybridSearchProperties;
import com.knowledgeforge.system.graph.service.GraphService;
import com.knowledgeforge.system.knowledge.repository.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSearchService {

    private static final String ROLE_ANCHOR = "anchor";
    private static final String ROLE_ADJACENT = "adjacent";
    private static final String ORIGIN_HYBRID = "hybrid";
    private static final String ORIGIN_ADJACENT = "adjacent";
    private static final String EXPANSION_REASON_SAME_SECTION = "adjacent_same_section";
    private static final String EXPANSION_REASON_FALLBACK_INDEX = "adjacent_fallback_index";

    private final RetrievalService retrievalService;
    private final KeywordSearchService keywordSearchService;
    private final GraphService graphService;
    private final DocumentChunkRepository chunkRepository;
    private final HybridSearchProperties hybridSearchProperties;

    public List<Document> hybridSearch(String query, UUID kbId) {
        return hybridSearch(query, kbId, null);
    }

    public List<Document> hybridSearch(String query, UUID kbId, RagObservation observation) {
        List<Document> vectorResults = timeStage(observation, "retrieval.vector",
                () -> retrievalService.retrieve(query, kbId));
        List<Document> keywordResults = timeStage(observation, "retrieval.keyword",
                () -> keywordSearchService.search(query, kbId, SystemConstants.RETRIEVAL_MAX_RESULTS));
        List<Document> graphResults = timeStage(observation, "retrieval.graph",
                () -> graphEnhancedSearch(query, kbId));

        List<Document> merged = timeStage(observation, "retrieval.rrf_merge",
                () -> rrfMerge(vectorResults, keywordResults, graphResults));
        List<Document> expanded = timeStage(observation, "retrieval.adjacent_expand",
                () -> expandWithAdjacentChunks(merged));
        log.info("混合检索: 向量={}条, 关键词={}条, 图谱={}条, 融合后={}条, 扩展后={}条",
                vectorResults.size(), keywordResults.size(), graphResults.size(), merged.size(), expanded.size());
        return expanded;
    }

    public List<Document> hybridSearchMultipleKbs(String query, List<UUID> kbIds) {
        return hybridSearchMultipleKbs(query, kbIds, null);
    }

    public List<Document> hybridSearchMultipleKbs(String query, List<UUID> kbIds, RagObservation observation) {
        List<Document> vectorResults = timeStage(observation, "retrieval.vector",
                () -> retrievalService.retrieveMultipleKbs(query, kbIds));

        List<Document> keywordResultsBuffer = new ArrayList<>();
        List<Document> graphResultsBuffer = new ArrayList<>();
        for (UUID kbId : kbIds) {
            keywordResultsBuffer.addAll(timeStage(observation, "retrieval.keyword",
                    () -> keywordSearchService.search(query, kbId, SystemConstants.RETRIEVAL_MAX_RESULTS)));
            graphResultsBuffer.addAll(timeStage(observation, "retrieval.graph",
                    () -> graphEnhancedSearch(query, kbId)));
        }
        List<Document> keywordResults = keywordResultsBuffer.stream()
                .sorted(Comparator.comparingDouble(
                        (Document d) -> ((Number) d.getMetadata().getOrDefault("keyword_score", 0.0)).doubleValue()
                ).reversed())
                .limit(SystemConstants.RETRIEVAL_MAX_RESULTS)
                .toList();
        List<Document> graphResults = graphResultsBuffer.stream()
                .sorted(Comparator.comparingDouble(
                        (Document d) -> ((Number) d.getMetadata().getOrDefault("graph_score", 0.0)).doubleValue()
                ).reversed())
                .limit(SystemConstants.RETRIEVAL_MAX_RESULTS)
                .toList();

        List<Document> merged = timeStage(observation, "retrieval.rrf_merge",
                () -> rrfMerge(vectorResults, keywordResults, graphResults));
        List<Document> expanded = timeStage(observation, "retrieval.adjacent_expand",
                () -> expandWithAdjacentChunks(merged));
        log.info("混合检索(多知识库): 向量={}条, 关键词={}条, 图谱={}条, 融合后={}条, 扩展后={}条",
                vectorResults.size(), keywordResults.size(), graphResults.size(), merged.size(), expanded.size());
        return expanded;
    }

    private <T> T timeStage(RagObservation observation, String stage, RagObservation.StageSupplier<T> supplier) {
        if (observation == null) {
            return supplier.get();
        }
        return observation.timeStage(stage, supplier);
    }

    private List<Document> rrfMerge(List<Document> vectorResults, List<Document> keywordResults, List<Document> graphResults) {
        Map<String, HybridScoreAccumulator> scoreMap = new HashMap<>();
        Map<String, Document> docMap = new HashMap<>();

        for (int i = 0; i < vectorResults.size(); i++) {
            Document doc = vectorResults.get(i);
            String chunkId = resolveChunkId(doc, i, "vec");
            double rrf = hybridSearchProperties.getWeights().getVector()
                    / (hybridSearchProperties.getRrf().getK() + i + 1);
            scoreMap.computeIfAbsent(chunkId, ignored -> new HybridScoreAccumulator())
                    .add(rrf, "vector");
            docMap.putIfAbsent(chunkId, doc);
        }

        for (int i = 0; i < keywordResults.size(); i++) {
            Document doc = keywordResults.get(i);
            String chunkId = resolveChunkId(doc, i, "kw");
            double rrf = hybridSearchProperties.getWeights().getKeyword()
                    / (hybridSearchProperties.getRrf().getK() + i + 1);
            scoreMap.computeIfAbsent(chunkId, ignored -> new HybridScoreAccumulator())
                    .add(rrf, "keyword");
            docMap.putIfAbsent(chunkId, doc);
        }

        for (int i = 0; i < graphResults.size(); i++) {
            Document doc = graphResults.get(i);
            String chunkId = resolveChunkId(doc, i, "gr");
            double rrf = hybridSearchProperties.getWeights().getGraph()
                    / (hybridSearchProperties.getRrf().getK() + i + 1);
            scoreMap.computeIfAbsent(chunkId, ignored -> new HybridScoreAccumulator())
                    .add(rrf, "graph");
            docMap.putIfAbsent(chunkId, doc);
        }

        log.info("RRF融合前: vector={}条, keyword={}条, graph={}条, minHybridScore={}, minEvidenceHits={}",
                vectorResults.size(),
                keywordResults.size(),
                graphResults.size(),
                hybridSearchProperties.getThresholds().getMinHybridScore(),
                hybridSearchProperties.getThresholds().getMinEvidenceHits());

        List<Map.Entry<String, HybridScoreAccumulator>> rankedEntries = scoreMap.entrySet().stream()
                .peek(entry -> logAnchorDecision(entry.getKey(), entry.getValue(), docMap.get(entry.getKey())))
                .filter(entry -> shouldRetainAnchor(entry.getValue()))
                .sorted(Comparator
                        .<Map.Entry<String, HybridScoreAccumulator>>comparingInt(entry -> entry.getValue().evidenceHits())
                        .reversed()
                        .thenComparing(entry -> entry.getValue().score(), Comparator.reverseOrder()))
                .limit(SystemConstants.RETRIEVAL_TOP_K)
                .toList();

        log.info("RRF融合后保留 {} 条 anchor", rankedEntries.size());

        List<Document> merged = new ArrayList<>();
        for (int i = 0; i < rankedEntries.size(); i++) {
            Map.Entry<String, HybridScoreAccumulator> entry = rankedEntries.get(i);
            Document doc = docMap.get(entry.getKey());
            if (doc == null) {
                continue;
            }
            HybridScoreAccumulator accumulator = entry.getValue();
            doc.getMetadata().put("hybrid_score", accumulator.score());
            doc.getMetadata().put("retrieval_role", ROLE_ANCHOR);
            doc.getMetadata().put("anchor_chunk_id", entry.getKey());
            doc.getMetadata().put("source_rank", i + 1);
            doc.getMetadata().put("retrieval_origin", ORIGIN_HYBRID);
            doc.getMetadata().put("evidence_hits", accumulator.evidenceHits());
            doc.getMetadata().put("matched_sources", accumulator.matchedSources());
            merged.add(doc);
        }
        return merged;
    }

    private List<Document> expandWithAdjacentChunks(List<Document> mergedResults) {
        if (mergedResults.isEmpty()) {
            return mergedResults;
        }

        Map<UUID, List<Document>> baseDocsByDocumentId = mergedResults.stream()
                .filter(doc -> parseUuid(doc.getMetadata().get("document_id")) != null)
                .collect(Collectors.groupingBy(doc -> parseUuid(doc.getMetadata().get("document_id")), LinkedHashMap::new,
                        Collectors.toList()));

        if (baseDocsByDocumentId.isEmpty()) {
            return mergedResults;
        }

        Map<UUID, Map<Integer, DocumentChunk>> adjacentChunkMap = loadAdjacentChunks(baseDocsByDocumentId);
        if (adjacentChunkMap.isEmpty()) {
            return mergedResults;
        }

        Set<String> seenChunkIds = new LinkedHashSet<>();
        List<Document> expanded = new ArrayList<>();
        for (Document doc : mergedResults) {
            appendIfAbsent(expanded, seenChunkIds, doc, expanded.size(), "anchor");
        }

        List<AdjacentCandidate> candidates = collectAdjacentCandidates(mergedResults, adjacentChunkMap);
        Map<String, Integer> adjacentCountsByAnchor = new HashMap<>();
        int appendedAdjacentCount = 0;
        for (AdjacentCandidate candidate : candidates) {
            if (appendedAdjacentCount >= hybridSearchProperties.getAdjacent().getMaxAdjacentChunks()) {
                break;
            }
            int currentAnchorAdjacentCount = adjacentCountsByAnchor.getOrDefault(candidate.anchorChunkId(), 0);
            if (currentAnchorAdjacentCount >= hybridSearchProperties.getAdjacent().getMaxAdjacentChunksPerAnchor()) {
                continue;
            }
            if (appendIfAbsent(expanded, seenChunkIds, candidate.document(), expanded.size(), "adjacent")) {
                adjacentCountsByAnchor.put(candidate.anchorChunkId(), currentAnchorAdjacentCount + 1);
                appendedAdjacentCount++;
            }
        }

        return expanded;
    }

    private List<AdjacentCandidate> collectAdjacentCandidates(List<Document> mergedResults,
                                                              Map<UUID, Map<Integer, DocumentChunk>> adjacentChunkMap) {
        List<AdjacentCandidate> candidates = new ArrayList<>();
        for (Document doc : mergedResults) {
            UUID documentId = parseUuid(doc.getMetadata().get("document_id"));
            Integer chunkIndex = asInteger(doc.getMetadata().get("chunk_index"));
            if (documentId == null || chunkIndex == null) {
                continue;
            }

            Map<Integer, DocumentChunk> documentChunks = adjacentChunkMap.get(documentId);
            if (documentChunks == null || documentChunks.isEmpty()) {
                continue;
            }

            String anchorChunkId = resolveAnchorChunkId(doc);
            Integer sourceRankValue = asInteger(doc.getMetadata().get("source_rank"));
            int sourceRank = sourceRankValue != null ? sourceRankValue : Integer.MAX_VALUE;
            double hybridScore = ((Number) doc.getMetadata().getOrDefault("hybrid_score", 0.0)).doubleValue();

            int chunkWindow = hybridSearchProperties.getAdjacent().getChunkWindow();
            for (int offset = -chunkWindow; offset <= chunkWindow; offset++) {
                if (offset == 0) {
                    continue;
                }
                DocumentChunk adjacentChunk = documentChunks.get(chunkIndex + offset);
                if (!shouldIncludeAdjacentChunk(doc, adjacentChunk, offset)) {
                    continue;
                }
                Document neighborDoc = toNeighborDocument(adjacentChunk, doc, offset);
                boolean sameSection = Boolean.TRUE.equals(neighborDoc.getMetadata().get("same_section"));
                candidates.add(new AdjacentCandidate(
                        anchorChunkId,
                        sourceRank,
                        hybridScore,
                        sameSection,
                        Math.abs(offset),
                        documentId,
                        adjacentChunk.getChunkIndex(),
                        neighborDoc
                ));
            }
        }

        candidates.sort(Comparator
                .comparing(AdjacentCandidate::sameSection).reversed()
                .thenComparingInt(AdjacentCandidate::distance)
                .thenComparingInt(AdjacentCandidate::sourceRank)
                .thenComparing(AdjacentCandidate::hybridScore, Comparator.reverseOrder())
                .thenComparing(AdjacentCandidate::documentId)
                .thenComparingInt(AdjacentCandidate::chunkIndex));
        return candidates;
    }

    private Map<UUID, Map<Integer, DocumentChunk>> loadAdjacentChunks(Map<UUID, List<Document>> baseDocsByDocumentId) {
        Map<UUID, Map<Integer, DocumentChunk>> chunkMap = new HashMap<>();
        for (Map.Entry<UUID, List<Document>> entry : baseDocsByDocumentId.entrySet()) {
            UUID documentId = entry.getKey();
            List<Integer> chunkIndexes = entry.getValue().stream()
                    .map(doc -> asInteger(doc.getMetadata().get("chunk_index")))
                    .filter(Objects::nonNull)
                    .toList();
            if (chunkIndexes.isEmpty()) {
                continue;
            }

            int minChunkIndex = chunkIndexes.get(0);
            int maxChunkIndex = chunkIndexes.get(0);
            for (Integer chunkIndex : chunkIndexes) {
                if (chunkIndex < minChunkIndex) {
                    minChunkIndex = chunkIndex;
                }
                if (chunkIndex > maxChunkIndex) {
                    maxChunkIndex = chunkIndex;
                }
            }
            int chunkWindow = hybridSearchProperties.getAdjacent().getChunkWindow();
            int minIndex = minChunkIndex - chunkWindow;
            int maxIndex = maxChunkIndex + chunkWindow;
            List<DocumentChunk> adjacentChunks = chunkRepository
                    .findByDocumentIdAndChunkIndexBetweenOrderByChunkIndexAsc(documentId,
                            Math.max(minIndex, 0), maxIndex);
            if (adjacentChunks.isEmpty()) {
                continue;
            }

            chunkMap.put(documentId, adjacentChunks.stream()
                    .collect(Collectors.toMap(chunk -> chunk.getChunkIndex(), chunk -> chunk, (left, right) -> left,
                            LinkedHashMap::new)));
        }
        return chunkMap;
    }

    private boolean appendIfAbsent(List<Document> target, Set<String> seenChunkIds, Document doc, int index,
                                 String fallbackPrefix) {
        String chunkKey = resolveChunkKey(doc, index, fallbackPrefix);
        if (seenChunkIds.add(chunkKey)) {
            target.add(doc);
            return true;
        }
        return false;
    }

    private String resolveChunkKey(Document doc, int index, String fallbackPrefix) {
        Object chunkId = doc.getMetadata().get("chunk_id");
        if (chunkId != null) {
            return chunkId.toString();
        }
        UUID documentId = parseUuid(doc.getMetadata().get("document_id"));
        Integer chunkIndex = asInteger(doc.getMetadata().get("chunk_index"));
        if (documentId != null && chunkIndex != null) {
            return documentId + ":" + chunkIndex;
        }
        return resolveChunkId(doc, index, fallbackPrefix);
    }

    private boolean shouldIncludeAdjacentChunk(Document baseDoc, DocumentChunk adjacentChunk, int offset) {
        if (adjacentChunk == null || offset == 0) {
            return false;
        }
        Integer baseStartOffset = asInteger(baseDoc.getMetadata().get("start_offset"));
        Integer baseEndOffset = asInteger(baseDoc.getMetadata().get("end_offset"));
        if (baseStartOffset != null && baseEndOffset != null
                && adjacentChunk.getStartOffset() != null && adjacentChunk.getEndOffset() != null
                && isHighlyOverlapping(baseStartOffset, baseEndOffset,
                adjacentChunk.getStartOffset(), adjacentChunk.getEndOffset())) {
            return false;
        }

        String baseSectionPath = normalizeSectionPath(baseDoc.getMetadata().get("section_path"));
        String adjacentSectionPath = normalizeSectionPath(adjacentChunk.getSectionPath());
        if (baseSectionPath == null || adjacentSectionPath == null) {
            return false;
        }
        return baseSectionPath.equals(adjacentSectionPath);
    }

    private boolean shouldRetainAnchor(HybridScoreAccumulator accumulator) {
        return accumulator.evidenceHits() >= hybridSearchProperties.getThresholds().getMinEvidenceHits()
                || accumulator.score() >= hybridSearchProperties.getThresholds().getMinHybridScore();
    }

    private void logAnchorDecision(String chunkId, HybridScoreAccumulator accumulator, Document doc) {
        boolean retained = shouldRetainAnchor(accumulator);
        String dropReason = retained ? "retained"
                : accumulator.evidenceHits() < hybridSearchProperties.getThresholds().getMinEvidenceHits()
                && accumulator.score() < hybridSearchProperties.getThresholds().getMinHybridScore()
                ? "below_min_evidence_hits_and_min_hybrid_score"
                : accumulator.evidenceHits() < hybridSearchProperties.getThresholds().getMinEvidenceHits()
                ? "below_min_evidence_hits"
                : "below_min_hybrid_score";
        Object vectorSimilarity = doc != null ? doc.getMetadata().get("vector_similarity") : null;
        Object keywordScore = doc != null ? doc.getMetadata().get("keyword_score") : null;
        Object graphScore = doc != null ? doc.getMetadata().get("graph_score") : null;
        log.info("RRF anchor决策: chunkId={}, retained={}, dropReason={}, evidenceHits={}, hybridScore={}, matchedSources={}, vectorSimilarity={}, keywordScore={}, graphScore={}",
                chunkId,
                retained,
                dropReason,
                accumulator.evidenceHits(),
                accumulator.score(),
                accumulator.matchedSources(),
                vectorSimilarity,
                keywordScore,
                graphScore);
    }

    private String resolveAnchorChunkId(Document doc) {
        return Objects.toString(doc.getMetadata().getOrDefault("anchor_chunk_id",
                doc.getMetadata().get("chunk_id")), "unknown-anchor");
    }

    private static final class HybridScoreAccumulator {
        private double score;
        private final Set<String> matchedSources = new LinkedHashSet<>();

        private void add(double delta, String source) {
            matchedSources.add(source);
            score += delta;
        }

        private double score() {
            return score;
        }

        private int evidenceHits() {
            return matchedSources.size();
        }

        private Set<String> matchedSources() {
            return Set.copyOf(matchedSources);
        }
    }

    private record AdjacentCandidate(String anchorChunkId,
                                     int sourceRank,
                                     double hybridScore,
                                     boolean sameSection,
                                     int distance,
                                     UUID documentId,
                                     int chunkIndex,
                                     Document document) {
    }

    private boolean isHighlyOverlapping(int startA, int endA, int startB, int endB) {
        int overlapStart = Math.max(startA, startB);
        int overlapEnd = Math.min(endA, endB);
        if (overlapEnd <= overlapStart) {
            return false;
        }
        int overlapLength = overlapEnd - overlapStart;
        int minLength = Math.min(endA - startA, endB - startB);
        return minLength > 0 && overlapLength * 2 >= minLength;
    }

    private String normalizeSectionPath(Object value) {
        if (value == null) {
            return null;
        }
        String sectionPath = value.toString().trim();
        return sectionPath.isEmpty() ? null : sectionPath;
    }

    private Document toNeighborDocument(DocumentChunk chunk, Document baseDoc, int offset) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("chunk_id", chunk.getId().toString());
        metadata.put("document_id", chunk.getDocumentId().toString());
        metadata.put("chunk_index", chunk.getChunkIndex());
        metadata.put("adjacent", true);
        metadata.put("adjacent_to", baseDoc.getMetadata().get("chunk_id"));
        metadata.put("retrieval_role", ROLE_ADJACENT);
        metadata.put("retrieval_origin", ORIGIN_ADJACENT);
        metadata.put("anchor_chunk_id", baseDoc.getMetadata().getOrDefault("anchor_chunk_id",
                baseDoc.getMetadata().get("chunk_id")));
        metadata.put("expansion_offset", offset);
        metadata.put("same_section", isSameSection(baseDoc, chunk));
        metadata.put("expansion_reason", resolveExpansionReason(baseDoc, chunk));
        metadata.put("hybrid_score", ((Number) baseDoc.getMetadata().getOrDefault("hybrid_score", 0.0)).doubleValue());
        copyIfPresent(baseDoc.getMetadata(), metadata, "kb_id");
        copyIfPresent(baseDoc.getMetadata(), metadata, "vector_similarity");
        copyIfPresent(baseDoc.getMetadata(), metadata, "keyword_score");
        copyIfPresent(baseDoc.getMetadata(), metadata, "graph_score");
        copyIfPresent(baseDoc.getMetadata(), metadata, "source_rank");
        if (chunk.getParentChunkId() != null) {
            metadata.put("parent_chunk_id", chunk.getParentChunkId().toString());
        }
        if (chunk.getSectionTitle() != null) {
            metadata.put("section_title", chunk.getSectionTitle());
        }
        if (chunk.getSectionPath() != null) {
            metadata.put("section_path", chunk.getSectionPath());
        }
        if (chunk.getChunkType() != null) {
            metadata.put("chunk_type", chunk.getChunkType());
        }
        if (chunk.getStrategyVersion() != null) {
            metadata.put("strategy_version", chunk.getStrategyVersion());
        }
        if (chunk.getStartOffset() != null) {
            metadata.put("start_offset", chunk.getStartOffset());
        }
        if (chunk.getEndOffset() != null) {
            metadata.put("end_offset", chunk.getEndOffset());
        }
        return new Document(chunk.getContent(), metadata);
    }

    private boolean isSameSection(Document baseDoc, DocumentChunk chunk) {
        String baseSectionPath = normalizeSectionPath(baseDoc.getMetadata().get("section_path"));
        String chunkSectionPath = normalizeSectionPath(chunk.getSectionPath());
        if (baseSectionPath == null || chunkSectionPath == null) {
            return false;
        }
        return baseSectionPath.equals(chunkSectionPath);
    }

    private String resolveExpansionReason(Document baseDoc, DocumentChunk chunk) {
        return isSameSection(baseDoc, chunk) ? EXPANSION_REASON_SAME_SECTION : EXPANSION_REASON_FALLBACK_INDEX;
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        Object value = source.get(key);
        if (value != null) {
            target.put(key, value);
        }
    }

    private String resolveChunkId(Document doc, int index, String fallbackPrefix) {
        Object chunkId = doc.getMetadata().get("chunk_id");
        if (chunkId != null) {
            return chunkId.toString();
        }
        return doc.getText().hashCode() + "_" + fallbackPrefix + "_" + index;
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private UUID parseUuid(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private List<Document> graphEnhancedSearch(String query, UUID kbId) {
        try {
            List<UUID> relatedChunkIds = graphService.findRelatedChunks(kbId, query);
            if (relatedChunkIds.isEmpty()) {
                return List.of();
            }

            List<DocumentChunk> chunks = chunkRepository.findAllById(relatedChunkIds);
            Map<UUID, DocumentChunk> chunkMap = chunks.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(chunk -> Objects.requireNonNull(chunk).getId(), chunk -> chunk));

            List<Document> docs = new ArrayList<>();
            for (int i = 0; i < relatedChunkIds.size(); i++) {
                UUID chunkId = relatedChunkIds.get(i);
                double score = 1.0 / (i + 1);

                DocumentChunk chunk = chunkMap.get(chunkId);
                String content = chunk != null ? chunk.getContent() : "";

                Document doc = new Document(content);
                doc.getMetadata().put("chunk_id", chunkId.toString());
                doc.getMetadata().put("graph_score", score);
                doc.getMetadata().put("kb_id", kbId.toString());
                if (chunk != null) {
                    doc.getMetadata().put("document_id", chunk.getDocumentId().toString());
                    doc.getMetadata().put("chunk_index", chunk.getChunkIndex());
                    if (chunk.getParentChunkId() != null) {
                        doc.getMetadata().put("parent_chunk_id", chunk.getParentChunkId().toString());
                    }
                    if (chunk.getSectionTitle() != null) {
                        doc.getMetadata().put("section_title", chunk.getSectionTitle());
                    }
                    if (chunk.getSectionPath() != null) {
                        doc.getMetadata().put("section_path", chunk.getSectionPath());
                    }
                    if (chunk.getChunkType() != null) {
                        doc.getMetadata().put("chunk_type", chunk.getChunkType());
                    }
                    if (chunk.getStrategyVersion() != null) {
                        doc.getMetadata().put("strategy_version", chunk.getStrategyVersion());
                    }
                }
                docs.add(doc);
            }
            return docs;
        } catch (Exception e) {
            log.warn("图谱检索失败: {}", e.getMessage());
            return List.of();
        }
    }
}
