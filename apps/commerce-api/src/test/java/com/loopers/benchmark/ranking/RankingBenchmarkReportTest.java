package com.loopers.benchmark.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class RankingBenchmarkReportTest {
    @TempDir
    Path tempDir;

    @DisplayName("Redis 조회와 전체 HTTP 결과를 CSV와 Markdown으로 기록한다.")
    @Test
    void writesCsvAndMarkdown() throws Exception {
        RankingBenchmarkConfig config = new RankingBenchmarkConfig(
            List.of(1000), List.of(20), 10, 2, 1, 1, tempDir, "m2 local"
        );
        RankingBenchmarkStatistics.Summary summary =
            new RankingBenchmarkStatistics.Summary(2.0, 1.0, 3.0, 3.0, 3.0, 500.0);
        List<RankingBenchmarkResult> results = List.of(
            new RankingBenchmarkResult("redis-repository", 1000, 20, 1, 2, 2, 0, 4.0, summary, 96000, 96.0),
            new RankingBenchmarkResult("full-http", 1000, 20, 1, 2, 2, 0, 8.0, summary, 96000, 96.0)
        );

        RankingBenchmarkReport.ReportFiles files = RankingBenchmarkReport.write(
            config,
            new RankingBenchmarkEnvironment("21", "macOS", "aarch64", 8, "Asia/Seoul", "7.2.5"),
            results,
            Instant.parse("2026-07-16T10:30:00Z")
        );

        String csv = Files.readString(files.csv());
        String markdown = Files.readString(files.markdown());
        assertAll(
            () -> assertThat(files.csv().getFileName().toString()).startsWith("20260716-193000-000-m2-local"),
            () -> assertThat(csv).contains("throughput_rps,avg_ms,p50_ms,p95_ms,p99_ms,max_ms")
                .contains("redis_memory_bytes,redis_bytes_per_member")
                .contains("redis-repository").contains("full-http"),
            () -> assertThat(markdown).contains("Fixed ranking date: 2026-07-16")
                .contains("Redis version: 7.2.5")
                .contains("Redis bytes | Bytes/member")
                .contains("MySQL product/brand aggregation")
                .contains("p99 is only a reference")
        );
    }
}
