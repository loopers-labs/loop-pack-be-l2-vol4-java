package com.loopers.benchmark.ranking;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingBenchmarkConfigTest {

    @Test
    void parsesConfigurableBenchmarkProfile() {
        RankingBenchmarkConfig config = RankingBenchmarkConfig.from(Map.ofEntries(
            Map.entry("rankingBenchmarkEvents", "1200"),
            Map.entry("rankingBenchmarkBatchSizes", "1,100,1000"),
            Map.entry("rankingBenchmarkHotCardinality", "12"),
            Map.entry("rankingBenchmarkUniformCardinality", "1200"),
            Map.entry("rankingBenchmarkRuns", "3"),
            Map.entry("rankingBenchmarkWarmupEvents", "100"),
            Map.entry("rankingBenchmarkRetryBatchSizes", "50,500"),
            Map.entry("rankingBenchmarkFailurePoints", "25,75"),
            Map.entry("rankingBenchmarkOutputDir", "build/custom-ranking"),
            Map.entry("rankingBenchmarkLabel", "local-fast")
        ));

        assertThat(config.events()).isEqualTo(1200);
        assertThat(config.batchSizes()).containsExactly(1, 100, 1000);
        assertThat(config.hotCardinality()).isEqualTo(12);
        assertThat(config.uniformCardinality()).isEqualTo(1200);
        assertThat(config.retryBatchSizes()).containsExactly(50, 500);
        assertThat(config.failurePoints()).containsExactly(25, 75);
        assertThat(config.outputDir()).isEqualTo(Path.of("build/custom-ranking"));
        assertThat(config.label()).isEqualTo("local-fast");
    }

    @Test
    void rejectsOutOfRangeFailurePoint() {
        assertThatThrownBy(() -> RankingBenchmarkConfig.from(Map.of("rankingBenchmarkFailurePoints", "25,100")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("rankingBenchmarkFailurePoints");
    }
}
