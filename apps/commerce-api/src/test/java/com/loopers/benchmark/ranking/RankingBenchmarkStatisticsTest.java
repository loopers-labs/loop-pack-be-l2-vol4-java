package com.loopers.benchmark.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class RankingBenchmarkStatisticsTest {
    @DisplayName("지연시간 백분위와 실제 경과시간 기준 처리량을 계산한다.")
    @Test
    void summarizesLatencyAndThroughput() {
        RankingBenchmarkStatistics.Summary summary = RankingBenchmarkStatistics.summarize(
            List.of(40.0, 10.0, 30.0, 20.0), 200.0
        );

        assertAll(
            () -> assertThat(summary.avgMs()).isEqualTo(25.0),
            () -> assertThat(summary.p50Ms()).isEqualTo(20.0),
            () -> assertThat(summary.p95Ms()).isEqualTo(40.0),
            () -> assertThat(summary.p99Ms()).isEqualTo(40.0),
            () -> assertThat(summary.maxMs()).isEqualTo(40.0),
            () -> assertThat(summary.throughputRps()).isEqualTo(20.0)
        );
    }

    @DisplayName("측정값이 없으면 통계를 생성하지 않는다.")
    @Test
    void rejectsEmptyMeasurements() {
        assertThatThrownBy(() -> RankingBenchmarkStatistics.summarize(List.of(), 10.0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("latency");
    }
}
