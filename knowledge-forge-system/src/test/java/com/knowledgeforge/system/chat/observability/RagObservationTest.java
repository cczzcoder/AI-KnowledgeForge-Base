package com.knowledgeforge.system.chat.observability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RagObservationTest {

    @Test
    void recordStage_recordsRetrievalMetric() {
        RagMetricsAggregator aggregator = new RagMetricsAggregator(10, 10);
        RagObservation observation = new RagObservation(aggregator, RagObservation.Mode.SYNC);

        observation.recordStage("retrieval.total", 25);

        RagMetricsAggregator.MetricSnapshot snapshot = aggregator.getSnapshot("rag.retrieval.total");
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.sampleSize()).isEqualTo(1);
        assertThat(snapshot.p50()).isEqualTo(25);
        assertThat(snapshot.p95()).isEqualTo(25);
        assertThat(snapshot.max()).isEqualTo(25);
    }

    @Test
    void recordStage_recordsContextBuildMetric() {
        RagMetricsAggregator aggregator = new RagMetricsAggregator(10, 10);
        RagObservation observation = new RagObservation(aggregator, RagObservation.Mode.SYNC);

        observation.recordStage("context.build", 18);

        RagMetricsAggregator.MetricSnapshot snapshot = aggregator.getSnapshot("rag.context.build");
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.sampleSize()).isEqualTo(1);
        assertThat(snapshot.p50()).isEqualTo(18);
        assertThat(snapshot.p95()).isEqualTo(18);
        assertThat(snapshot.max()).isEqualTo(18);
    }

    @Test
    void recordStage_recordsSourcesBuildMetric() {
        RagMetricsAggregator aggregator = new RagMetricsAggregator(10, 10);
        RagObservation observation = new RagObservation(aggregator, RagObservation.Mode.SYNC);

        observation.recordStage("sources.build", 12);

        RagMetricsAggregator.MetricSnapshot snapshot = aggregator.getSnapshot("rag.sources.build");
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.sampleSize()).isEqualTo(1);
        assertThat(snapshot.p50()).isEqualTo(12);
        assertThat(snapshot.p95()).isEqualTo(12);
        assertThat(snapshot.max()).isEqualTo(12);
    }

    @Test
    void finishSuccess_recordsSyncFullResponseMetric() {
        RagMetricsAggregator aggregator = new RagMetricsAggregator(10, 10);
        RagObservation observation = new RagObservation(aggregator, RagObservation.Mode.SYNC);

        long duration = observation.finishSuccess();

        RagMetricsAggregator.MetricSnapshot snapshot = aggregator.getSnapshot("rag.sync.full_response");
        assertThat(duration).isGreaterThanOrEqualTo(0);
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.sampleSize()).isEqualTo(1);
        assertThat(snapshot.p50()).isEqualTo(duration);
        assertThat(snapshot.p95()).isEqualTo(duration);
        assertThat(snapshot.max()).isEqualTo(duration);
    }

    @Test
    void finishSuccess_recordsStreamFullResponseMetric() {
        RagMetricsAggregator aggregator = new RagMetricsAggregator(10, 10);
        RagObservation observation = new RagObservation(aggregator, RagObservation.Mode.STREAM);

        long duration = observation.finishSuccess();

        RagMetricsAggregator.MetricSnapshot snapshot = aggregator.getSnapshot("rag.stream.full_response");
        assertThat(duration).isGreaterThanOrEqualTo(0);
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.sampleSize()).isEqualTo(1);
        assertThat(snapshot.p50()).isEqualTo(duration);
        assertThat(snapshot.p95()).isEqualTo(duration);
        assertThat(snapshot.max()).isEqualTo(duration);
    }

    @Test
    void markFirstToken_recordsMetricOnlyOnce() {
        RagMetricsAggregator aggregator = new RagMetricsAggregator(10, 10);
        RagObservation observation = new RagObservation(aggregator, RagObservation.Mode.STREAM);

        observation.markFirstToken();
        observation.markFirstToken();

        RagMetricsAggregator.MetricSnapshot snapshot = aggregator.getSnapshot("rag.stream.first_token");
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.sampleSize()).isEqualTo(1);
        assertThat(observation.getFirstTokenMs()).isEqualTo(snapshot.p50());
        assertThat(snapshot.p95()).isEqualTo(snapshot.p50());
        assertThat(snapshot.max()).isEqualTo(snapshot.p50());
    }

    @Test
    void setKbMode_fallsBackToNoneForBlankValues() {
        RagMetricsAggregator aggregator = new RagMetricsAggregator(10, 10);
        RagObservation observation = new RagObservation(aggregator, RagObservation.Mode.SYNC);

        observation.setKbMode(null);
        assertThat(observation.getKbMode()).isEqualTo("none");

        observation.setKbMode("   ");
        assertThat(observation.getKbMode()).isEqualTo("none");
    }
}
