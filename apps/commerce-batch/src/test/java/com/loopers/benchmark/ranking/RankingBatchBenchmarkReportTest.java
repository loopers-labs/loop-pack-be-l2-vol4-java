package com.loopers.benchmark.ranking;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RankingBatchBenchmarkReportTest {
    @TempDir
    Path tempDir;

    @Test
    void writesRawCsvAndClaimBoundedReport() throws Exception {
        RankingBatchBenchmarkConfig config = RankingBatchBenchmarkConfig.from(Map.of(
            "rankingBatchBenchmarkCardinalities", "100",
            "rankingBatchBenchmarkChunkSizes", "100",
            "rankingBatchBenchmarkOutputDir", tempDir.toString(),
            "rankingBatchBenchmarkLabel", "test"
        ));
        RankingBatchBenchmarkResult throughput = new RankingBatchBenchmarkResult(
            1, 100, 450, 100, 1, 100, 10.0, 45_000.0, 10_000.0,
            100, 1, 42.0, "digest", "success"
        );
        RankingBatchRecoveryResult recovery = new RankingBatchRecoveryResult(
            100, 100, "a", "a", "a", "a", true, true, true
        );
        RankingBatchFreshnessResult freshness = new RankingBatchFreshnessResult(
            1, 100, 100, 10.0, 12.0, 15.0, 1_010.0, 1_012.0, 1_015.0
        );

        RankingBatchBenchmarkReport.ReportFiles files = RankingBatchBenchmarkReport.write(
            config, List.of(throughput), List.of(recovery), List.of(freshness), Instant.parse("2026-07-17T00:00:00Z")
        );

        files.verifyWritten();
        assertThat(java.nio.file.Files.readString(files.throughputCsv()))
            .contains("publisher_scope,production_chunk_size")
            .contains("metric_units")
            .contains("benchmark-equivalent,500");
        assertThat(java.nio.file.Files.readString(files.markdown()))
            .contains("production capacity가 아니다")
            .contains("Kafka E2E")
            .contains("standalone Redis")
            .contains("chunk size는 500");
    }
}
