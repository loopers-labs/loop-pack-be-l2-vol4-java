package com.loopers.benchmark.ranking.period;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PeriodRankingBenchmarkConfigTest {
    @Test
    void usesSmokeFriendlyDefaults() {
        PeriodRankingBenchmarkConfig config = PeriodRankingBenchmarkConfig.from(Map.of());

        assertThat(config.cardinalities()).containsExactly(1_000, 5_000);
        assertThat(config.periodDays()).isEqualTo(7);
        assertThat(config.chunkSizes()).containsExactly(100, 500, 1_000);
        assertThat(config.outputDir()).isEqualTo(Path.of("build/reports/period-ranking"));
    }

    @Test
    void parsesEveryProperty() {
        PeriodRankingBenchmarkConfig config = PeriodRankingBenchmarkConfig.from(Map.ofEntries(
            Map.entry("periodRankingBenchmarkCardinalities", "10,20"),
            Map.entry("periodRankingBenchmarkPeriodDays", "30"),
            Map.entry("periodRankingBenchmarkActiveHours", "2"),
            Map.entry("periodRankingBenchmarkPageSize", "25"),
            Map.entry("periodRankingBenchmarkChunkSizes", "50,100"),
            Map.entry("periodRankingBenchmarkRuns", "4"),
            Map.entry("periodRankingBenchmarkWarmup", "0"),
            Map.entry("periodRankingBenchmarkContentionRuns", "5"),
            Map.entry("periodRankingBenchmarkHoldMs", "250"),
            Map.entry("periodRankingBenchmarkOutputDir", "out"),
            Map.entry("periodRankingBenchmarkLabel", " profile ")
        ));

        assertThat(config.cardinalities()).containsExactly(10, 20);
        assertThat(config.periodDays()).isEqualTo(30);
        assertThat(config.activeHours()).isEqualTo(2);
        assertThat(config.pageSize()).isEqualTo(25);
        assertThat(config.chunkSizes()).containsExactly(50, 100);
        assertThat(config.runs()).isEqualTo(4);
        assertThat(config.warmupRuns()).isZero();
        assertThat(config.contentionRuns()).isEqualTo(5);
        assertThat(config.holdMs()).isEqualTo(250);
        assertThat(config.label()).isEqualTo("profile");
    }

    @Test
    void rejectsInvalidInput() {
        assertThatThrownBy(() -> PeriodRankingBenchmarkConfig.from(Map.of(
            "periodRankingBenchmarkChunkSizes", "100,100"
        ))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate");
        assertThatThrownBy(() -> PeriodRankingBenchmarkConfig.from(Map.of(
            "periodRankingBenchmarkActiveHours", "25"
        ))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("between 1 and 24");
        assertThatThrownBy(() -> PeriodRankingBenchmarkConfig.from(Map.of(
            "periodRankingBenchmarkHoldMs", "-1"
        ))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("zero or greater");
    }

    @Test
    void plansEveryChunkSizeForEachWarmupRun() {
        PeriodRankingBenchmarkConfig config = PeriodRankingBenchmarkConfig.from(Map.of(
            "periodRankingBenchmarkChunkSizes", "100,500,1000",
            "periodRankingBenchmarkWarmup", "2"
        ));

        assertThat(config.chunkWarmupPlan()).containsExactly(100, 500, 1_000, 100, 500, 1_000);
    }
}
