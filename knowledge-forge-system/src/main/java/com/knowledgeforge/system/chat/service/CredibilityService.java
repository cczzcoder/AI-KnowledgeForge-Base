package com.knowledgeforge.system.chat.service;

import com.knowledgeforge.core.entity.Document;
import com.knowledgeforge.system.chat.dto.CredibilityBreakdownDTO;
import com.knowledgeforge.system.conversation.repository.ChatMessageRepository;
import com.knowledgeforge.system.knowledge.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CredibilityService {

    private static final double SIMILARITY_WEIGHT = 0.4;
    private static final double FRESHNESS_WEIGHT = 0.2;
    private static final double DIVERSITY_WEIGHT = 0.2;
    private static final double HISTORY_WEIGHT = 0.2;
    private static final int FRESHNESS_DECAY_DAYS = 90;

    private final DocumentRepository documentRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Transactional(readOnly = true)
    public CredibilityBreakdownDTO calculateCredibility(
            List<org.springframework.ai.document.Document> retrievedDocs,
            UUID kbId) {
        if (retrievedDocs == null || retrievedDocs.isEmpty()) {
            return CredibilityBreakdownDTO.builder()
                    .similarityScore(0)
                    .freshnessScore(0)
                    .diversityScore(0)
                    .historyScore(0)
                    .overallScore(0)
                    .build();
        }

        double similarityScore = calculateAvgSimilarity(retrievedDocs);
        double freshnessScore = calculateFreshness(retrievedDocs);
        double diversityScore = calculateSourceDiversity(retrievedDocs);
        double historyScore = calculateHistoricalAccuracy();

        double overallScore = similarityScore * SIMILARITY_WEIGHT
                + freshnessScore * FRESHNESS_WEIGHT
                + diversityScore * DIVERSITY_WEIGHT
                + historyScore * HISTORY_WEIGHT;

        return CredibilityBreakdownDTO.builder()
                .similarityScore(round(similarityScore))
                .freshnessScore(round(freshnessScore))
                .diversityScore(round(diversityScore))
                .historyScore(round(historyScore))
                .overallScore(round(overallScore))
                .build();
    }

    private double calculateAvgSimilarity(List<org.springframework.ai.document.Document> docs) {
        double total = 0;
        int count = 0;
        for (var doc : docs) {
            Object score = doc.getMetadata().get("similarity_score");
            if (score instanceof Number) {
                total += ((Number) score).doubleValue();
                count++;
            }
        }
        if (count == 0) {
            Object vecScore = docs.getFirst().getMetadata().get("score");
            if (vecScore instanceof Number) {
                total = ((Number) vecScore).doubleValue();
                count = 1;
            }
        }
        return count > 0 ? Math.min(total / count, 1.0) : 0.5;
    }

    private double calculateFreshness(List<org.springframework.ai.document.Document> docs) {
        Set<UUID> docIds = new HashSet<>();
        for (var doc : docs) {
            Object docIdObj = doc.getMetadata().get("document_id");
            if (docIdObj != null) {
                try {
                    docIds.add(UUID.fromString(docIdObj.toString()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        if (docIds.isEmpty()) {
            return 0.5;
        }

        List<Document> documents = documentRepository.findAllById(docIds);
        if (documents.isEmpty()) {
            return 0.5;
        }

        LocalDateTime now = LocalDateTime.now();
        double totalFreshness = 0;
        for (var document : documents) {
            long daysSinceCreation = ChronoUnit.DAYS.between(document.getCreatedAt(), now);
            double decayFactor = Math.exp(-((double) daysSinceCreation / FRESHNESS_DECAY_DAYS));
            totalFreshness += decayFactor;
        }

        return totalFreshness / documents.size();
    }

    private double calculateSourceDiversity(List<org.springframework.ai.document.Document> docs) {
        Set<String> uniqueDocIds = new HashSet<>();
        for (var doc : docs) {
            Object docId = doc.getMetadata().get("document_id");
            if (docId != null) {
                uniqueDocIds.add(docId.toString());
            }
        }

        if (docs.isEmpty() || uniqueDocIds.isEmpty()) {
            return 0;
        }

        double ratio = (double) uniqueDocIds.size() / docs.size();
        return Math.min(ratio * 1.5, 1.0);
    }

    private double calculateHistoricalAccuracy() {
        long positiveCount = chatMessageRepository.countByFeedback("POSITIVE");
        long negativeCount = chatMessageRepository.countByFeedback("NEGATIVE");
        long totalFeedback = positiveCount + negativeCount;

        if (totalFeedback == 0) {
            return 0.5;
        }

        return (double) positiveCount / totalFeedback;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}