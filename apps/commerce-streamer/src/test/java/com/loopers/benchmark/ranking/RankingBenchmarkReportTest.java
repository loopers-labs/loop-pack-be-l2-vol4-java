package com.loopers.benchmark.ranking;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RankingBenchmarkReportTest {

    @TempDir
    Path tempDir;

    @Test
    void writesMachineReadableCsvAndMarkdown() throws Exception {
        RankingBenchmarkConfig config = RankingBenchmarkConfig.from(MapBuilder.withOutputDir(tempDir));
        RankingAggregationBenchmarkResult aggregation = new RankingAggregationBenchmarkResult(
            "hot", 100, 1, 1, 1000, 10, 25.0, 40_000.0, 100, 0.1,
            new RankingBenchmarkStatistics.LatencySummary(2.5, 2.0, 4.0, 5.0), 100.0
        );
        RankingRetryDriftBenchmarkResult retry = new RankingRetryDriftBenchmarkResult(
            500, 25, 1000, 50, 100.0, 110.0, 10.0, 25, 80.0
        );

        RankingBenchmarkReport.ReportFiles files = RankingBenchmarkReport.write(
            config, List.of(aggregation), List.of(retry), Instant.parse("2026-07-17T00:00:00Z")
        );

        assertThat(Files.readString(files.aggregationCsv())).contains("distribution,batch_size", "hot,100");
        assertThat(Files.readString(files.retryCsv())).contains("failure_point_percent", "500,25");
        assertThat(Files.readString(files.markdown())).contains("Ranking Streamer Benchmark", "Top 20 overlap");
    }

    private static final class MapBuilder {
        private static java.util.Map<String, String> withOutputDir(Path outputDir) {
            return java.util.Map.of("rankingBenchmarkOutputDir", outputDir.toString());
        }
    }
}
