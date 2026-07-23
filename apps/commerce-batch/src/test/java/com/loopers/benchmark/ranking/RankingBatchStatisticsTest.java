package com.loopers.benchmark.ranking;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingBatchStatisticsTest {
    @Test
    void calculatesNearestRankPercentiles() {
        List<Double> samples = List.of(5.0, 1.0, 3.0, 2.0, 4.0);

        assertThat(RankingBatchStatistics.percentile(samples, 50)).isEqualTo(3.0);
        assertThat(RankingBatchStatistics.percentile(samples, 95)).isEqualTo(5.0);
        assertThat(RankingBatchStatistics.percentile(samples, 100)).isEqualTo(5.0);
    }

    @Test
    void rejectsEmptyMeasurements() {
        assertThatThrownBy(() -> RankingBatchStatistics.percentile(List.of(), 50))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
