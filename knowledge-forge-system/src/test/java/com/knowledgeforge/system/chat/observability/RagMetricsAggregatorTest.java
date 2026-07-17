package com.knowledgeforge.system.chat.observability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RagMetricsAggregatorTest {

    @Test
    void record_computesPercentilesWithinWindow() {
        RagMetricsAggregator aggregator = new RagMetricsAggregator(5, 10);

        aggregator.record("rag.sync.full_response", 10);
        aggregator.record("rag.sync.full_response", 20);
        aggregator.record("rag.sync.full_response", 30);
        aggregator.record("rag.sync.full_response", 40);
        aggregator.record("rag.sync.full_response", 50);

        RagMetricsAggregator.MetricSnapshot snapshot = aggregator.getSnapshot("rag.sync.full_response");
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.sampleSize()).isEqualTo(5);
        assertThat(snapshot.p50()).isEqualTo(30);
        assertThat(snapshot.p95()).isEqualTo(50);
        assertThat(snapshot.max()).isEqualTo(50);
    }

    @Test
    void record_evictsOldestSamplesWhenWindowIsFull() {
        RagMetricsAggregator aggregator = new RagMetricsAggregator(3, 10);

        aggregator.record("rag.stream.first_token", 10);
        aggregator.record("rag.stream.first_token", 20);
        aggregator.record("rag.stream.first_token", 30);
        aggregator.record("rag.stream.first_token", 40);

        RagMetricsAggregator.MetricSnapshot snapshot = aggregator.getSnapshot("rag.stream.first_token");
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.sampleSize()).isEqualTo(3);
        assertThat(snapshot.p50()).isEqualTo(30);
        assertThat(snapshot.p95()).isEqualTo(40);
        assertThat(snapshot.max()).isEqualTo(40);
    }
}
