package com.loopers.benchmark.ranking;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RankingBenchmarkStatisticsTest {

    @Test
    void usesNearestRankPercentiles() {
        RankingBenchmarkStatistics.LatencySummary summary = RankingBenchmarkStatistics.summarize(
            List.of(4.0, 1.0, 3.0, 2.0, 100.0)
        );

        assertThat(summary.averageMs()).isEqualTo(22.0);
        assertThat(summary.p50Ms()).isEqualTo(3.0);
        assertThat(summary.p95Ms()).isEqualTo(100.0);
        assertThat(summary.p99Ms()).isEqualTo(100.0);
    }
}
