package com.knowledgeforge.system.chat.observability;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class RagMetricsAggregator {

    static final int DEFAULT_WINDOW_SIZE = 200;
    static final int DEFAULT_ROLLUP_FREQUENCY = 50;

    private final int windowSize;
    private final int rollupFrequency;
    private final Map<String, MetricWindow> windows = new ConcurrentHashMap<>();

    public RagMetricsAggregator() {
        this(DEFAULT_WINDOW_SIZE, DEFAULT_ROLLUP_FREQUENCY);
    }

    public RagMetricsAggregator(int windowSize, int rollupFrequency) {
        this.windowSize = windowSize;
        this.rollupFrequency = rollupFrequency;
    }

    public void record(String metricName, long durationMs) {
        if (metricName == null || metricName.isBlank() || durationMs < 0) {
            return;
        }

        MetricWindow window = windows.computeIfAbsent(metricName,
                ignored -> new MetricWindow(windowSize, rollupFrequency));
        MetricSnapshot snapshot = window.record(durationMs);
        if (snapshot != null) {
            log.info("event=rag_metric_rollup metric={} sampleSize={} p50={}ms p95={}ms max={}ms",
                    metricName, snapshot.sampleSize(), snapshot.p50(), snapshot.p95(), snapshot.max());
        }
    }

    MetricSnapshot snapshot(String metricName) {
        MetricWindow window = windows.get(metricName);
        return window == null ? null : window.snapshot();
    }

    record MetricSnapshot(int sampleSize, long p50, long p95, long max) {
    }

    public MetricSnapshot getSnapshot(String metricName) {
        return snapshot(metricName);
    }

    private static final class MetricWindow {
        private final int windowSize;
        private final int rollupFrequency;
        private final ConcurrentLinkedDeque<Long> samples = new ConcurrentLinkedDeque<>();
        private final AtomicInteger sampleCount = new AtomicInteger();
        private final AtomicInteger sinceLastRollup = new AtomicInteger();

        private MetricWindow(int windowSize, int rollupFrequency) {
            this.windowSize = windowSize;
            this.rollupFrequency = rollupFrequency;
        }

        synchronized MetricSnapshot record(long durationMs) {
            samples.addLast(durationMs);
            int size = sampleCount.incrementAndGet();
            if (size > windowSize) {
                Long removed = samples.pollFirst();
                if (removed != null) {
                    sampleCount.decrementAndGet();
                }
            }

            int recordedSinceRollup = sinceLastRollup.incrementAndGet();
            if (recordedSinceRollup < rollupFrequency) {
                return null;
            }
            sinceLastRollup.set(0);
            return snapshot();
        }

        synchronized MetricSnapshot snapshot() {
            List<Long> values = new ArrayList<>(samples);
            if (values.isEmpty()) {
                return null;
            }
            values.sort(Long::compareTo);
            int size = values.size();
            long p50 = percentile(values, 0.50d);
            long p95 = percentile(values, 0.95d);
            long max = values.get(size - 1);
            return new MetricSnapshot(size, p50, p95, max);
        }

        private long percentile(List<Long> values, double percentile) {
            int index = (int) Math.ceil(values.size() * percentile) - 1;
            index = Math.max(0, Math.min(index, values.size() - 1));
            return values.get(index);
        }
    }
}
