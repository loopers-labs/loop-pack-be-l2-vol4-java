package com.loopers.benchmark.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class WaitingQueueBenchmarkStatisticsTest {

    @DisplayName("요청 지연시간의 평균과 nearest-rank p50, p95, p99, max를 요약한다.")
    @Test
    void summarizesLatencyWithNearestRank() {
        // arrange
        List<Double> latenciesMs = List.of(40.0, 10.0, 30.0, 20.0);

        // act
        WaitingQueueBenchmarkStatistics.LatencySummary summary =
            WaitingQueueBenchmarkStatistics.summarize(latenciesMs);

        // assert
        assertAll(
            () -> assertThat(summary.avgMs()).isEqualTo(25.0),
            () -> assertThat(summary.p50Ms()).isEqualTo(20.0),
            () -> assertThat(summary.p95Ms()).isEqualTo(40.0),
            () -> assertThat(summary.p99Ms()).isEqualTo(40.0),
            () -> assertThat(summary.maxMs()).isEqualTo(40.0)
        );
    }

    @DisplayName("측정값이 없으면 유효한 백분위를 만들 수 없으므로 거부한다.")
    @Test
    void rejectsEmptyLatencyMeasurements() {
        // arrange
        List<Double> latenciesMs = List.of();

        // act & assert
        assertThatThrownBy(() -> WaitingQueueBenchmarkStatistics.summarize(latenciesMs))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("latency");
    }
}
