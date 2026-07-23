package com.loopers.benchmark.ranking;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingBatchBenchmarkConfigTest {
    @Test
    void parsesBenchmarkProperties() {
        RankingBatchBenchmarkConfig config = RankingBatchBenchmarkConfig.from(Map.of(
            "rankingBatchBenchmarkCardinalities", "100,10000",
            "rankingBatchBenchmarkActiveHours", "12",
            "rankingBatchBenchmarkChunkSizes", "100,1000",
            "rankingBatchBenchmarkRuns", "5",
            "rankingBatchBenchmarkWarmup", "2",
            "rankingBatchBenchmarkFreshnessIntervals", "1,10,60",
            "rankingBatchBenchmarkOutputDir", "build/custom-batch",
            "rankingBatchBenchmarkLabel", "local-m2"
        ));

        assertThat(config.cardinalities()).containsExactly(100, 10_000);
        assertThat(config.activeHours()).isEqualTo(12);
        assertThat(config.chunkSizes()).containsExactly(100, 1_000);
        assertThat(config.runs()).isEqualTo(5);
        assertThat(config.warmupRuns()).isEqualTo(2);
        assertThat(config.freshnessIntervalsSeconds()).containsExactly(1, 10, 60);
        assertThat(config.label()).isEqualTo("local-m2");
    }

    @Test
    void rejectsInvalidActiveHoursAndDuplicates() {
        assertThatThrownBy(() -> RankingBatchBenchmarkConfig.from(Map.of(
            "rankingBatchBenchmarkActiveHours", "25"
        ))).hasMessageContaining("rankingBatchBenchmarkActiveHours");
        assertThatThrownBy(() -> RankingBatchBenchmarkConfig.from(Map.of(
            "rankingBatchBenchmarkChunkSizes", "100,100"
        ))).hasMessageContaining("rankingBatchBenchmarkChunkSizes");
    }
}
