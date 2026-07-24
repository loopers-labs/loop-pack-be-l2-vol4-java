package com.loopers.benchmark.ranking.period;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PeriodRankingBenchmarkReportTest {
    @TempDir
    Path tempDir;

    @Test
    void writesThreeCsvFilesAndMarkdownWithClaimBoundaryAndMedian() throws Exception {
        PeriodRankingBenchmarkConfig config = new PeriodRankingBenchmarkConfig(
            List.of(10), 7, 1, 5, List.of(5), 2, 0, 1, 10, tempDir, "CI profile"
        );
        List<PeriodRankingStrategyResult> strategies = List.of(
            new PeriodRankingStrategyResult(1, "legacy", 10, 70, 7, 1, 5, 10, 1_000, 2, 2, 10, "abc"),
            new PeriodRankingStrategyResult(2, "legacy", 10, 70, 7, 1, 5, 20, 500, 2, 2, 10, "abc")
        );
        List<PeriodRankingChunkResult> chunks = List.of(
            new PeriodRankingChunkResult(1, 10, 5, 5, 2_000, 2, "abc")
        );
        List<PeriodRankingContentionResult> contention = List.of(
            new PeriodRankingContentionResult(1, "READ_COMMITTED", 10, 2, 3, 1)
        );

        PeriodRankingBenchmarkReport.ReportFiles files = PeriodRankingBenchmarkReport.write(
            config,
            strategies,
            chunks,
            contention,
            new PeriodRankingBenchmarkReport.Environment("21", "test-os", "MySQL test"),
            Instant.parse("2026-07-24T00:00:00Z")
        );

        files.verifyWritten();
        assertThat(Files.readString(files.strategyCsv())).contains("source_group_query_count", "legacy");
        assertThat(Files.readString(files.chunkCsv())).contains("chunk_size");
        assertThat(Files.readString(files.contentionCsv())).contains("source_update_latency_ms");
        assertThat(Files.readString(files.markdown()))
            .contains("Claim boundary", "Median summary", "15.0000", "MySQL test");
    }
}
