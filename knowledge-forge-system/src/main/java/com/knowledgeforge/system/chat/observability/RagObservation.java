package com.knowledgeforge.system.chat.observability;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
public class RagObservation {

    public enum Mode {
        SYNC,
        STREAM
    }

    private final RagMetricsAggregator metricsAggregator;
    @Getter
    private final Mode mode;
    private final long requestStartNano;
    private final Map<String, Long> stageDurations = new LinkedHashMap<>();

    @Getter
    private UUID conversationId;
    @Getter
    private String kbMode;
    @Getter
    private int retrievedCount;
    @Getter
    private int sourceCount;
    @Getter
    private int answerLength;
    @Getter
    private Long firstTokenMs;
    @Getter
    private Long fullResponseMs;
    @Getter
    private boolean success;
    @Getter
    private String errorType;

    public RagObservation(RagMetricsAggregator metricsAggregator, Mode mode) {
        this.metricsAggregator = metricsAggregator;
        this.mode = mode;
        this.requestStartNano = System.nanoTime();
        this.kbMode = "none";
    }

    public void setConversationId(UUID conversationId) {
        this.conversationId = conversationId;
    }

    public void setKbMode(String kbMode) {
        this.kbMode = kbMode == null || kbMode.isBlank() ? "none" : kbMode;
    }

    public void setRetrievedCount(int retrievedCount) {
        this.retrievedCount = retrievedCount;
    }

    public void setSourceCount(int sourceCount) {
        this.sourceCount = sourceCount;
    }

    public void setAnswerLength(int answerLength) {
        this.answerLength = answerLength;
    }

    public <T> T timeStage(String stage, StageSupplier<T> supplier) {
        long start = System.nanoTime();
        try {
            return supplier.get();
        } finally {
            recordStage(stage, elapsedMillis(start));
        }
    }

    public void timeStage(String stage, StageRunnable runnable) {
        long start = System.nanoTime();
        try {
            runnable.run();
        } finally {
            recordStage(stage, elapsedMillis(start));
        }
    }

    public void recordStage(String stage, long durationMs) {
        stageDurations.put(stage, durationMs);
        log.info("event=rag_stage conversationId={} mode={} kbMode={} stage={} durationMs={}",
                conversationId, modeName(), kbMode, stage, durationMs);
        if ("retrieval.total".equals(stage)) {
            metricsAggregator.record("rag.retrieval.total", durationMs);
        }
        if ("context.build".equals(stage)) {
            metricsAggregator.record("rag.context.build", durationMs);
        }
        if ("sources.build".equals(stage)) {
            metricsAggregator.record("rag.sources.build", durationMs);
        }
        if ("llm.call".equals(stage)) {
            metricsAggregator.record("rag.llm.call", durationMs);
        }
    }

    public void markFirstToken() {
        if (firstTokenMs != null) {
            return;
        }
        firstTokenMs = elapsedSinceRequestStart();
        metricsAggregator.record("rag.stream.first_token", firstTokenMs);
        log.info("event=rag_stage conversationId={} mode={} kbMode={} stage=first_token durationMs={}",
                conversationId, modeName(), kbMode, firstTokenMs);
    }

    public long finishSuccess() {
        success = true;
        fullResponseMs = elapsedSinceRequestStart();
        metricsAggregator.record(metricNameForFullResponse(), fullResponseMs);
        log.info("event=rag_summary conversationId={} mode={} kbMode={} retrievedCount={} sourceCount={} answerLength={} firstTokenMs={} fullResponseMs={} success=true errorType={}",
                conversationId, modeName(), kbMode, retrievedCount, sourceCount, answerLength, firstTokenMs,
                fullResponseMs, null);
        return fullResponseMs;
    }

    public void finishError(Throwable error) {
        success = false;
        errorType = error == null ? null : error.getClass().getSimpleName();
        log.info("event=rag_summary conversationId={} mode={} kbMode={} retrievedCount={} sourceCount={} answerLength={} firstTokenMs={} fullResponseMs={} success=false errorType={}",
                conversationId, modeName(), kbMode, retrievedCount, sourceCount, answerLength, firstTokenMs,
                fullResponseMs, errorType);
    }

    public Map<String, Long> stageDurations() {
        return Map.copyOf(stageDurations);
    }

    private String metricNameForFullResponse() {
        return mode == Mode.STREAM ? "rag.stream.full_response" : "rag.sync.full_response";
    }

    private String modeName() {
        return mode.name().toLowerCase();
    }

    private long elapsedSinceRequestStart() {
        return elapsedMillis(requestStartNano);
    }

    private long elapsedMillis(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000L;
    }

    @FunctionalInterface
    public interface StageSupplier<T> {
        T get();
    }

    @FunctionalInterface
    public interface StageRunnable {
        void run();
    }
}
