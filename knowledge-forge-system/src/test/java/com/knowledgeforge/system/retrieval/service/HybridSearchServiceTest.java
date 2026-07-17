package com.knowledgeforge.system.retrieval.service;

import com.knowledgeforge.core.entity.DocumentChunk;
import com.knowledgeforge.system.config.HybridSearchProperties;
import com.knowledgeforge.system.graph.service.GraphService;
import com.knowledgeforge.system.knowledge.repository.DocumentChunkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HybridSearchServiceTest {

    @Mock
    private RetrievalService retrievalService;
    @Mock
    private KeywordSearchService keywordSearchService;
    @Mock
    private GraphService graphService;
    @Mock
    private DocumentChunkRepository chunkRepository;

    private HybridSearchService hybridSearchService;
    private HybridSearchProperties hybridSearchProperties;

    @BeforeEach
    void setUp() {
        hybridSearchProperties = new HybridSearchProperties();
        hybridSearchService = new HybridSearchService(
                retrievalService,
                keywordSearchService,
                graphService,
                chunkRepository,
                hybridSearchProperties
        );
    }

    @Test
    void hybridSearch_marksMergedHitsAsAnchor_andAddsHybridMetadata() {
        UUID kbId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();
        Document anchor = doc("anchor-content", Map.of(
                "chunk_id", chunkId.toString(),
                "document_id", documentId.toString(),
                "chunk_index", 1,
                "vector_similarity", 0.92,
                "keyword_score", 0.32
        ));

        when(retrievalService.retrieve("query", kbId)).thenReturn(List.of(anchor));
        when(keywordSearchService.search("query", kbId, 20)).thenReturn(List.of(doc("anchor-content", Map.of(
                "chunk_id", chunkId.toString(),
                "document_id", documentId.toString(),
                "chunk_index", 1,
                "keyword_score", 0.32
        ))));
        when(graphService.findRelatedChunks(kbId, "query")).thenReturn(List.of());
        when(chunkRepository.findByDocumentIdAndChunkIndexBetweenOrderByChunkIndexAsc(eq(documentId), anyInt(), anyInt()))
                .thenReturn(List.of());

        List<Document> results = hybridSearchService.hybridSearch("query", kbId);

        assertThat(results).hasSize(1);
        Document result = results.getFirst();
        assertThat(result.getMetadata())
                .containsEntry("retrieval_role", "anchor")
                .containsEntry("retrieval_origin", "hybrid")
                .containsEntry("anchor_chunk_id", chunkId.toString())
                .containsEntry("source_rank", 1);
        assertThat(result.getMetadata()).containsKey("hybrid_score");
    }

    @Test
    void hybridSearch_expandsAdjacentChunks_sameSection_andCarriesAnchorMetadata() {
        UUID kbId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID anchorChunkId = UUID.randomUUID();
        UUID nextChunkId = UUID.randomUUID();
        Document anchor = doc("anchor", Map.of(
                "chunk_id", anchorChunkId.toString(),
                "document_id", documentId.toString(),
                "chunk_index", 1,
                "section_path", "A/B",
                "start_offset", 100,
                "end_offset", 200,
                "vector_similarity", 0.91
        ));
        Document keywordAnchor = doc("anchor-keyword", Map.of(
                "chunk_id", anchorChunkId.toString(),
                "document_id", documentId.toString(),
                "chunk_index", 1,
                "section_path", "A/B",
                "keyword_score", 0.31
        ));
        DocumentChunk prev = chunk(UUID.randomUUID(), documentId, 0, "prev", "A/B", 0, 90);
        DocumentChunk current = chunk(anchorChunkId, documentId, 1, "anchor", "A/B", 100, 200);
        DocumentChunk next = chunk(nextChunkId, documentId, 2, "next", "A/B", 210, 300);

        when(retrievalService.retrieve("query", kbId)).thenReturn(List.of(anchor));
        when(keywordSearchService.search("query", kbId, 20)).thenReturn(List.of(keywordAnchor));
        when(graphService.findRelatedChunks(kbId, "query")).thenReturn(List.of());
        when(chunkRepository.findByDocumentIdAndChunkIndexBetweenOrderByChunkIndexAsc(documentId, 0, 2))
                .thenReturn(List.of(prev, current, next));

        List<Document> results = hybridSearchService.hybridSearch("query", kbId);

        assertThat(results).hasSize(2);
        assertThat(results.getFirst().getMetadata())
                .containsEntry("retrieval_role", "anchor")
                .containsEntry("anchor_chunk_id", anchorChunkId.toString());
        Document adjacentDoc = results.get(1);
        assertThat(adjacentDoc.getText()).isEqualTo("prev");
        assertThat(adjacentDoc.getMetadata())
                .containsEntry("adjacent", true)
                .containsEntry("retrieval_role", "adjacent")
                .containsEntry("retrieval_origin", "adjacent")
                .containsEntry("anchor_chunk_id", anchorChunkId.toString())
                .containsEntry("adjacent_to", anchorChunkId.toString())
                .containsEntry("expansion_offset", -1)
                .containsEntry("same_section", true)
                .containsEntry("expansion_reason", "adjacent_same_section");
        assertThat(adjacentDoc.getMetadata()).doesNotContainEntry("chunk_id", nextChunkId.toString());
    }

    @Test
    void hybridSearch_skipsAdjacentChunk_whenSectionPathDiffers_andBothKnown() {
        UUID kbId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID anchorChunkId = UUID.randomUUID();
        Document anchor = doc("anchor", Map.of(
                "chunk_id", anchorChunkId.toString(),
                "document_id", documentId.toString(),
                "chunk_index", 1,
                "section_path", "A/B",
                "start_offset", 100,
                "end_offset", 200,
                "vector_similarity", 0.88
        ));
        Document keywordAnchor = doc("anchor-keyword", Map.of(
                "chunk_id", anchorChunkId.toString(),
                "document_id", documentId.toString(),
                "chunk_index", 1,
                "section_path", "A/B",
                "keyword_score", 0.31
        ));
        DocumentChunk prev = chunk(UUID.randomUUID(), documentId, 0, "prev", "A/C", 0, 90);
        DocumentChunk current = chunk(anchorChunkId, documentId, 1, "anchor", "A/B", 100, 200);
        DocumentChunk next = chunk(UUID.randomUUID(), documentId, 2, "next", "A/C", 210, 300);

        when(retrievalService.retrieve("query", kbId)).thenReturn(List.of(anchor));
        when(keywordSearchService.search("query", kbId, 20)).thenReturn(List.of(keywordAnchor));
        when(graphService.findRelatedChunks(kbId, "query")).thenReturn(List.of());
        when(chunkRepository.findByDocumentIdAndChunkIndexBetweenOrderByChunkIndexAsc(documentId, 0, 2))
                .thenReturn(List.of(prev, current, next));

        List<Document> results = hybridSearchService.hybridSearch("query", kbId);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getMetadata().get("retrieval_role")).isEqualTo("anchor");
    }

    @Test
    void hybridSearch_skipsAdjacentChunk_whenHighlyOverlapping() {
        UUID kbId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID anchorChunkId = UUID.randomUUID();
        Document anchor = doc("anchor", Map.of(
                "chunk_id", anchorChunkId.toString(),
                "document_id", documentId.toString(),
                "chunk_index", 1,
                "section_path", "A/B",
                "start_offset", 100,
                "end_offset", 200,
                "vector_similarity", 0.87
        ));
        Document keywordAnchor = doc("anchor-keyword", Map.of(
                "chunk_id", anchorChunkId.toString(),
                "document_id", documentId.toString(),
                "chunk_index", 1,
                "section_path", "A/B",
                "keyword_score", 0.31
        ));
        DocumentChunk prev = chunk(UUID.randomUUID(), documentId, 0, "prev", "A/B", 120, 180);
        DocumentChunk current = chunk(anchorChunkId, documentId, 1, "anchor", "A/B", 100, 200);
        DocumentChunk next = chunk(UUID.randomUUID(), documentId, 2, "next", "A/B", 210, 300);

        when(retrievalService.retrieve("query", kbId)).thenReturn(List.of(anchor));
        when(keywordSearchService.search("query", kbId, 20)).thenReturn(List.of(keywordAnchor));
        when(graphService.findRelatedChunks(kbId, "query")).thenReturn(List.of());
        when(chunkRepository.findByDocumentIdAndChunkIndexBetweenOrderByChunkIndexAsc(documentId, 0, 2))
                .thenReturn(List.of(prev, current, next));

        List<Document> results = hybridSearchService.hybridSearch("query", kbId);

        assertThat(results).hasSize(2);
        assertThat(results.get(1).getMetadata()).containsEntry("expansion_offset", 1);
    }

    @Test
    void hybridSearch_deduplicatesOverlappingAdjacentChunks_andKeepsAnchorsFirst() {
        UUID kbId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID anchorChunkId1 = UUID.randomUUID();
        UUID anchorChunkId2 = UUID.randomUUID();
        UUID sharedNeighborId = UUID.randomUUID();
        Document anchorOne = doc("anchor-1", Map.of(
                "chunk_id", anchorChunkId1.toString(),
                "document_id", documentId.toString(),
                "chunk_index", 1,
                "section_path", "A/B",
                "start_offset", 100,
                "end_offset", 180,
                "vector_similarity", 0.95
        ));
        Document anchorTwo = doc("anchor-2", Map.of(
                "chunk_id", anchorChunkId2.toString(),
                "document_id", documentId.toString(),
                "chunk_index", 3,
                "section_path", "A/B",
                "start_offset", 260,
                "end_offset", 340,
                "vector_similarity", 0.91
        ));
        Document keywordAnchorOne = doc("anchor-1-keyword", Map.of(
                "chunk_id", anchorChunkId1.toString(),
                "document_id", documentId.toString(),
                "chunk_index", 1,
                "section_path", "A/B",
                "keyword_score", 0.34
        ));
        Document keywordAnchorTwo = doc("anchor-2-keyword", Map.of(
                "chunk_id", anchorChunkId2.toString(),
                "document_id", documentId.toString(),
                "chunk_index", 3,
                "section_path", "A/B",
                "keyword_score", 0.3
        ));
        DocumentChunk leftNeighbor = chunk(UUID.randomUUID(), documentId, 0, "left", "A/B", 0, 90);
        DocumentChunk sharedNeighbor = chunk(sharedNeighborId, documentId, 2, "shared", "A/B", 190, 250);
        DocumentChunk rightNeighbor = chunk(UUID.randomUUID(), documentId, 4, "right", "A/B", 350, 430);

        when(retrievalService.retrieve("query", kbId)).thenReturn(List.of(anchorOne, anchorTwo));
        when(keywordSearchService.search("query", kbId, 20)).thenReturn(List.of(keywordAnchorOne, keywordAnchorTwo));
        when(graphService.findRelatedChunks(kbId, "query")).thenReturn(List.of());
        when(chunkRepository.findByDocumentIdAndChunkIndexBetweenOrderByChunkIndexAsc(documentId, 0, 4))
                .thenReturn(List.of(leftNeighbor, sharedNeighbor, rightNeighbor));

        List<Document> results = hybridSearchService.hybridSearch("query", kbId);

        assertThat(results).hasSize(4);
        assertThat(results.subList(0, 2)).extracting(Document::getText)
                .containsExactly("anchor-1", "anchor-2");
        assertThat(results).extracting(Document::getText)
                .containsExactly("anchor-1", "anchor-2", "left", "shared");
        assertThat(results.stream().filter(doc -> "shared".equals(doc.getText())).count()).isEqualTo(1);
        assertThat(results).allSatisfy(doc -> {
            if ("anchor-1".equals(doc.getText()) || "anchor-2".equals(doc.getText())) {
                assertThat(doc.getMetadata().get("retrieval_role")).isEqualTo("anchor");
            }
        });
    }

    @Test
    void hybridSearch_skipsExpansion_whenAnchorMetadataIsMalformed() {
        UUID kbId = UUID.randomUUID();
        Document malformedDocId = doc("bad-doc-id", Map.of(
                "chunk_id", UUID.randomUUID().toString(),
                "document_id", "not-a-uuid",
                "chunk_index", 1,
                "vector_similarity", 0.81,
                "keyword_score", 0.22
        ));
        Document malformedChunkIndex = doc("bad-chunk-index", Map.of(
                "chunk_id", UUID.randomUUID().toString(),
                "document_id", UUID.randomUUID().toString(),
                "chunk_index", "NaN",
                "vector_similarity", 0.8,
                "keyword_score", 0.21
        ));

        when(retrievalService.retrieve("query", kbId)).thenReturn(List.of(malformedDocId, malformedChunkIndex));
        when(keywordSearchService.search("query", kbId, 20)).thenReturn(List.of(
                doc("bad-doc-id-keyword", Map.of(
                        "chunk_id", malformedDocId.getMetadata().get("chunk_id").toString(),
                        "document_id", "not-a-uuid",
                        "chunk_index", 1,
                        "keyword_score", 0.22
                )),
                doc("bad-chunk-index-keyword", Map.of(
                        "chunk_id", malformedChunkIndex.getMetadata().get("chunk_id").toString(),
                        "document_id", malformedChunkIndex.getMetadata().get("document_id").toString(),
                        "chunk_index", "NaN",
                        "keyword_score", 0.21
                ))
        ));
        when(graphService.findRelatedChunks(kbId, "query")).thenReturn(List.of());

        List<Document> results = hybridSearchService.hybridSearch("query", kbId);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(Document::getText)
                .containsExactly("bad-doc-id", "bad-chunk-index");
    }

    @Test
    void hybridSearchMultipleKbs_expandsNeighborsWithinEachDocumentOnly() {
        UUID kb1 = UUID.randomUUID();
        UUID kb2 = UUID.randomUUID();
        UUID documentId1 = UUID.randomUUID();
        UUID documentId2 = UUID.randomUUID();
        UUID anchorChunkId1 = UUID.randomUUID();
        UUID anchorChunkId2 = UUID.randomUUID();
        Document anchorOne = doc("anchor-kb1", Map.of(
                "chunk_id", anchorChunkId1.toString(),
                "document_id", documentId1.toString(),
                "chunk_index", 1,
                "section_path", "doc1/section",
                "start_offset", 100,
                "end_offset", 180,
                "vector_similarity", 0.95
        ));
        Document anchorTwo = doc("anchor-kb2", Map.of(
                "chunk_id", anchorChunkId2.toString(),
                "document_id", documentId2.toString(),
                "chunk_index", 1,
                "section_path", "doc2/section",
                "start_offset", 100,
                "end_offset", 180,
                "vector_similarity", 0.93
        ));
        Document keywordAnchorOne = doc("anchor-kb1-keyword", Map.of(
                "chunk_id", anchorChunkId1.toString(),
                "document_id", documentId1.toString(),
                "chunk_index", 1,
                "section_path", "doc1/section",
                "keyword_score", 0.3
        ));
        Document keywordAnchorTwo = doc("anchor-kb2-keyword", Map.of(
                "chunk_id", anchorChunkId2.toString(),
                "document_id", documentId2.toString(),
                "chunk_index", 1,
                "section_path", "doc2/section",
                "keyword_score", 0.29
        ));
        DocumentChunk doc1Prev = chunk(UUID.randomUUID(), documentId1, 0, "doc1-prev", "doc1/section", 0, 90);
        DocumentChunk doc1Next = chunk(UUID.randomUUID(), documentId1, 2, "doc1-next", "doc1/section", 190, 260);
        DocumentChunk doc2Prev = chunk(UUID.randomUUID(), documentId2, 0, "doc2-prev", "doc2/section", 0, 90);
        DocumentChunk doc2Next = chunk(UUID.randomUUID(), documentId2, 2, "doc2-next", "doc2/section", 190, 260);

        when(retrievalService.retrieveMultipleKbs("query", List.of(kb1, kb2))).thenReturn(List.of(anchorOne, anchorTwo));
        when(keywordSearchService.search("query", kb1, 20)).thenReturn(List.of(keywordAnchorOne));
        when(keywordSearchService.search("query", kb2, 20)).thenReturn(List.of(keywordAnchorTwo));
        when(graphService.findRelatedChunks(kb1, "query")).thenReturn(List.of());
        when(graphService.findRelatedChunks(kb2, "query")).thenReturn(List.of());
        when(chunkRepository.findByDocumentIdAndChunkIndexBetweenOrderByChunkIndexAsc(documentId1, 0, 2))
                .thenReturn(List.of(doc1Prev, chunk(anchorChunkId1, documentId1, 1, "anchor-kb1", "doc1/section", 100, 180), doc1Next));
        when(chunkRepository.findByDocumentIdAndChunkIndexBetweenOrderByChunkIndexAsc(documentId2, 0, 2))
                .thenReturn(List.of(doc2Prev, chunk(anchorChunkId2, documentId2, 1, "anchor-kb2", "doc2/section", 100, 180), doc2Next));

        List<Document> results = hybridSearchService.hybridSearchMultipleKbs("query", List.of(kb1, kb2));

        assertThat(results).extracting(Document::getText)
                .containsExactly("anchor-kb1", "anchor-kb2", "doc1-prev", "doc2-prev");
        assertThat(results.subList(2, 4)).allMatch(doc -> {
            Object documentId = doc.getMetadata().get("document_id");
            return documentId1.toString().equals(documentId) || documentId2.toString().equals(documentId);
        });
    }
    @Test
    void hybridSearchMultipleKbs_usesTwoArgOverloadWithoutObservation_andPreservesBehavior() {
        UUID kb1 = UUID.randomUUID();
        UUID kb2 = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();
        Document anchor = doc("anchor-multi", Map.of(
                "chunk_id", chunkId.toString(),
                "document_id", documentId.toString(),
                "chunk_index", 1,
                "section_path", "A/B",
                "start_offset", 100,
                "end_offset", 180,
                "vector_similarity", 0.95
        ));
        Document keywordAnchor = doc("anchor-multi-keyword", Map.of(
                "chunk_id", chunkId.toString(),
                "document_id", documentId.toString(),
                "chunk_index", 1,
                "section_path", "A/B",
                "keyword_score", 0.3
        ));
        DocumentChunk prev = chunk(UUID.randomUUID(), documentId, 0, "prev", "A/B", 0, 90);
        DocumentChunk current = chunk(chunkId, documentId, 1, "anchor-multi", "A/B", 100, 180);
        DocumentChunk next = chunk(UUID.randomUUID(), documentId, 2, "next", "A/B", 190, 260);

        when(retrievalService.retrieveMultipleKbs("query", List.of(kb1, kb2))).thenReturn(List.of(anchor));
        when(keywordSearchService.search("query", kb1, 20)).thenReturn(List.of(keywordAnchor));
        when(keywordSearchService.search("query", kb2, 20)).thenReturn(List.of());
        when(graphService.findRelatedChunks(kb1, "query")).thenReturn(List.of());
        when(graphService.findRelatedChunks(kb2, "query")).thenReturn(List.of());
        when(chunkRepository.findByDocumentIdAndChunkIndexBetweenOrderByChunkIndexAsc(documentId, 0, 2))
                .thenReturn(List.of(prev, current, next));

        List<Document> results = hybridSearchService.hybridSearchMultipleKbs("query", List.of(kb1, kb2));

        assertThat(results).extracting(Document::getText)
                .containsExactly("anchor-multi", "prev");
        assertThat(results.getFirst().getMetadata())
                .containsEntry("retrieval_role", "anchor")
                .containsEntry("retrieval_origin", "hybrid");
        assertThat(results.get(1).getMetadata())
                .containsEntry("retrieval_role", "adjacent")
                .containsEntry("retrieval_origin", "adjacent")
                .containsEntry("same_section", true)
                .containsEntry("expansion_offset", -1);
    }

    @Test
    void hybridSearch_prefersChunksBackedByMultipleRetrievalSignals() {
        UUID kbId = UUID.randomUUID();
        UUID strongSingleChunkId = UUID.randomUUID();
        UUID dualHitChunkId = UUID.randomUUID();

        Document vectorStrongSingle = doc("vector-strong-single", Map.of(
                "chunk_id", strongSingleChunkId.toString(),
                "document_id", UUID.randomUUID().toString(),
                "chunk_index", 0,
                "vector_similarity", 1.0
        ));
        Document vectorDual = doc("vector-dual", Map.of(
                "chunk_id", dualHitChunkId.toString(),
                "document_id", UUID.randomUUID().toString(),
                "chunk_index", 1,
                "vector_similarity", 0.95
        ));
        Document keywordDual = doc("keyword-dual", Map.of(
                "chunk_id", dualHitChunkId.toString(),
                "document_id", UUID.randomUUID().toString(),
                "chunk_index", 1,
                "keyword_score", 0.42
        ));

        when(retrievalService.retrieve("query", kbId)).thenReturn(List.of(vectorStrongSingle, vectorDual));
        when(keywordSearchService.search("query", kbId, 20)).thenReturn(List.of(keywordDual));
        when(graphService.findRelatedChunks(kbId, "query")).thenReturn(List.of());

        List<Document> results = hybridSearchService.hybridSearch("query", kbId);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getMetadata())
                .containsEntry("chunk_id", dualHitChunkId.toString())
                .containsEntry("evidence_hits", 2)
                .containsEntry("matched_sources", Set.of("vector", "keyword"))
                .containsEntry("source_rank", 1);
    }

    @Test
    void hybridSearch_filtersWeakSingleSignalHits_insteadOfBackfillingTopK() {
        UUID kbId = UUID.randomUUID();

        Document weakVector = doc("weak-vector", Map.of(
                "chunk_id", UUID.randomUUID().toString(),
                "document_id", UUID.randomUUID().toString(),
                "chunk_index", 4,
                "vector_similarity", 0.21
        ));
        Document weakKeyword = doc("weak-keyword", Map.of(
                "chunk_id", UUID.randomUUID().toString(),
                "document_id", UUID.randomUUID().toString(),
                "chunk_index", 7,
                "keyword_score", 0.18
        ));
        Document dualHitVector = doc("dual-hit-vector", Map.of(
                "chunk_id", UUID.randomUUID().toString(),
                "document_id", UUID.randomUUID().toString(),
                "chunk_index", 1,
                "vector_similarity", 0.91
        ));
        String dualHitChunkId = dualHitVector.getMetadata().get("chunk_id").toString();
        Document dualHitKeyword = doc("dual-hit-keyword", Map.of(
                "chunk_id", dualHitChunkId,
                "document_id", UUID.randomUUID().toString(),
                "chunk_index", 1,
                "keyword_score", 0.33
        ));

        when(retrievalService.retrieve("query", kbId)).thenReturn(List.of(dualHitVector, weakVector));
        when(keywordSearchService.search("query", kbId, 20)).thenReturn(List.of(dualHitKeyword, weakKeyword));
        when(graphService.findRelatedChunks(kbId, "query")).thenReturn(List.of());

        List<Document> results = hybridSearchService.hybridSearch("query", kbId);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getText()).isEqualTo("dual-hit-vector");
        assertThat(results.getFirst().getMetadata())
                .containsEntry("evidence_hits", 2)
                .containsEntry("matched_sources", Set.of("vector", "keyword"));
    }

    @Test
    void hybridSearch_retainsTopSingleVectorHit_whenMinHybridScoreCrossesBoundary() {
        UUID kbId = UUID.randomUUID();
        hybridSearchProperties.getThresholds().setMinEvidenceHits(2);
        hybridSearchProperties.getThresholds().setMinHybridScore(0.0114);

        Document singleVector = doc("single-vector", Map.of(
                "chunk_id", UUID.randomUUID().toString(),
                "document_id", UUID.randomUUID().toString(),
                "chunk_index", 0,
                "vector_similarity", 0.91
        ));

        when(retrievalService.retrieve("query", kbId)).thenReturn(List.of(singleVector));
        when(keywordSearchService.search("query", kbId, 20)).thenReturn(List.of());
        when(graphService.findRelatedChunks(kbId, "query")).thenReturn(List.of());

        List<Document> results = hybridSearchService.hybridSearch("query", kbId);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getText()).isEqualTo("single-vector");
        assertThat(results.getFirst().getMetadata())
                .containsEntry("evidence_hits", 1)
                .containsEntry("matched_sources", Set.of("vector"));
    }

    private Document doc(String text, Map<String, Object> metadata) {
        return new Document(text, metadata);
    }

    private DocumentChunk chunk(UUID id, UUID documentId, int index, String content, String sectionPath,
                                Integer startOffset, Integer endOffset) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setId(id);
        chunk.setDocumentId(documentId);
        chunk.setChunkIndex(index);
        chunk.setContent(content);
        chunk.setSectionPath(sectionPath);
        chunk.setStartOffset(startOffset);
        chunk.setEndOffset(endOffset);
        return chunk;
    }
}
