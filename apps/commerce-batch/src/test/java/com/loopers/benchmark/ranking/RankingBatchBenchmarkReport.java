package com.loopers.benchmark.ranking;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

final class RankingBatchBenchmarkReport {
    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private RankingBatchBenchmarkReport() {
    }

    static ReportFiles write(
        RankingBatchBenchmarkConfig config,
        List<RankingBatchBenchmarkResult> throughput,
        List<RankingBatchRecoveryResult> recovery,
        List<RankingBatchFreshnessResult> freshness,
        Instant generatedAt
    ) throws IOException {
        if (throughput.isEmpty() || recovery.isEmpty() || freshness.isEmpty()) {
            throw new IllegalArgumentException("all benchmark result groups are required");
        }
        Files.createDirectories(config.outputDir());
        String suffix = generatedAt.atZone(REPORT_ZONE).format(FILE_TIME) + "-" + safe(config.label());
        Path throughputCsv = config.outputDir().resolve("ranking-batch-throughput-" + suffix + ".csv");
        Path recoveryCsv = config.outputDir().resolve("ranking-batch-recovery-" + suffix + ".csv");
        Path freshnessCsv = config.outputDir().resolve("ranking-batch-freshness-" + suffix + ".csv");
        Path markdown = config.outputDir().resolve("ranking-batch-" + suffix + ".md");
        Files.writeString(throughputCsv, throughputCsv(config, throughput), StandardCharsets.UTF_8);
        Files.writeString(recoveryCsv, recoveryCsv(config, recovery), StandardCharsets.UTF_8);
        Files.writeString(freshnessCsv, freshnessCsv(config, freshness), StandardCharsets.UTF_8);
        Files.writeString(markdown, markdown(config, throughput, recovery, freshness, generatedAt), StandardCharsets.UTF_8);
        return new ReportFiles(throughputCsv, recoveryCsv, freshnessCsv, markdown);
    }

    static String throughputCsv(RankingBatchBenchmarkConfig config, List<RankingBatchBenchmarkResult> results) {
        StringBuilder csv = new StringBuilder("label,publisher_scope,production_chunk_size,run,cardinality,metric_units,source_rows,active_hours,chunk_size,elapsed_ms,metric_units_per_sec,products_per_sec,redis_member_mutations,redis_command_or_chunk_count,total_score,score_digest,status\n");
        for (RankingBatchBenchmarkResult result : results) {
            csv.append(config.label()).append(',').append(BenchmarkEquivalentSnapshotPublisher.PUBLISHER_SCOPE).append(',')
                .append(BenchmarkEquivalentSnapshotPublisher.PRODUCTION_CHUNK_SIZE).append(',').append(result.run()).append(',')
                .append(result.cardinality()).append(',').append(result.metricUnits()).append(',')
                .append(result.sourceRows()).append(',').append(result.activeHours()).append(',')
                .append(result.chunkSize()).append(',').append(number(result.elapsedMs())).append(',')
                .append(number(result.metricUnitsPerSecond())).append(',').append(number(result.productsPerSecond())).append(',')
                .append(result.redisMemberMutations()).append(',').append(result.redisCommandOrChunkCount()).append(',')
                .append(number(result.totalScore())).append(',').append(result.scoreDigest()).append(',')
                .append(result.status()).append('\n');
        }
        return csv.toString();
    }

    static String recoveryCsv(RankingBatchBenchmarkConfig config, List<RankingBatchRecoveryResult> results) {
        StringBuilder csv = new StringBuilder("label,publisher_scope,production_chunk_size,cardinality,chunk_size,baseline_digest,rerun_digest,digest_after_injected_failure,digest_after_retry,rerun_exact,canonical_unchanged_on_failure,retry_exact\n");
        for (RankingBatchRecoveryResult result : results) {
            csv.append(config.label()).append(',').append(BenchmarkEquivalentSnapshotPublisher.PUBLISHER_SCOPE).append(',')
                .append(BenchmarkEquivalentSnapshotPublisher.PRODUCTION_CHUNK_SIZE).append(',').append(result.cardinality()).append(',')
                .append(result.chunkSize()).append(',').append(result.baselineDigest()).append(',')
                .append(result.rerunDigest()).append(',').append(result.digestAfterInjectedFailure()).append(',')
                .append(result.digestAfterRetry()).append(',').append(result.rerunExact()).append(',')
                .append(result.canonicalUnchangedOnFailure()).append(',').append(result.retryExact()).append('\n');
        }
        return csv.toString();
    }

    static String freshnessCsv(RankingBatchBenchmarkConfig config, List<RankingBatchFreshnessResult> results) {
        StringBuilder csv = new StringBuilder("label,interval_seconds,basis_cardinality,basis_chunk_size,runtime_p50_ms,runtime_p95_ms,runtime_max_ms,derived_freshness_p50_ms,derived_freshness_p95_ms,derived_freshness_max_ms\n");
        for (RankingBatchFreshnessResult result : results) {
            csv.append(config.label()).append(',').append(result.intervalSeconds()).append(',')
                .append(result.basisCardinality()).append(',').append(result.basisChunkSize()).append(',')
                .append(number(result.runtimeP50Ms())).append(',').append(number(result.runtimeP95Ms())).append(',')
                .append(number(result.runtimeMaxMs())).append(',').append(number(result.derivedFreshnessP50Ms())).append(',')
                .append(number(result.derivedFreshnessP95Ms())).append(',').append(number(result.derivedFreshnessMaxMs())).append('\n');
        }
        return csv.toString();
    }

    private static String markdown(
        RankingBatchBenchmarkConfig config,
        List<RankingBatchBenchmarkResult> throughput,
        List<RankingBatchRecoveryResult> recovery,
        List<RankingBatchFreshnessResult> freshness,
        Instant generatedAt
    ) {
        StringBuilder report = new StringBuilder("# Ranking Batch Snapshot Benchmark\n\n")
            .append("Generated at: ").append(generatedAt.atZone(REPORT_ZONE)).append("\n\n")
            .append("## Claim boundary\n\n")
            .append("- `publisher_scope=benchmark-equivalent`: SQL `GROUP BY`와 shared `RankingScoreFormula`는 production 코드를 직접 사용하지만, chunk-size 비교를 위해 Redis temp ZSET + Lua `RENAME` publisher는 production 알고리즘과 동등한 benchmark 전용 구현이다.\n")
            .append("- Production `DailyRankingSnapshotPublisher`의 chunk size는 500으로 고정되어 있다. 이 수치를 production 구현 자체의 처리량으로 해석하면 안 된다.\n")
            .append("- MySQL/Redis Testcontainers 및 same-JVM 측정이며 production capacity가 아니다. Kafka E2E, 이벤트의 DB ingest, scheduler 지연, 네트워크 분리 비용은 포함하지 않는다.\n")
            .append("- timer에는 SQL GROUP BY reader, score formula, temporary multi-ZADD, Lua publish가 포함된다. seed, Redis cleanup, correctness check, digest, report I/O는 timer 밖이다.\n")
            .append("- `metric_units`는 seed의 view/like/sales count delta 합계이며 실제 메시지 수가 아니다.\n")
            .append("- `RENAME` 원자성 결론은 standalone Redis 기준이다. Redis Cluster의 cross-slot 및 운영 failover semantics는 검증하지 않았다.\n\n")
            .append("## Throughput raw results\n\n")
            .append("| Run | Products | Metric units | Rows | Hours | Chunk | Elapsed ms | Metric units/s | Products/s | ZADD chunks | Status |\n")
            .append("| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |\n");
        for (RankingBatchBenchmarkResult result : throughput) {
            report.append("| ").append(result.run()).append(" | ").append(result.cardinality()).append(" | ")
                .append(result.metricUnits()).append(" | ").append(result.sourceRows()).append(" | ")
                .append(result.activeHours()).append(" | ").append(result.chunkSize()).append(" | ")
                .append(number(result.elapsedMs())).append(" | ").append(number(result.metricUnitsPerSecond())).append(" | ")
                .append(number(result.productsPerSecond())).append(" | ").append(result.redisCommandOrChunkCount())
                .append(" | ").append(result.status()).append(" |\n");
        }
        report.append("\n## Recovery and idempotency\n\n")
            .append("| Products | Chunk | Rerun exact | Canonical unchanged on build failure | Retry exact |\n")
            .append("| ---: | ---: | --- | --- | --- |\n");
        for (RankingBatchRecoveryResult result : recovery) {
            report.append("| ").append(result.cardinality()).append(" | ").append(result.chunkSize()).append(" | ")
                .append(result.rerunExact()).append(" | ").append(result.canonicalUnchangedOnFailure()).append(" | ")
                .append(result.retryExact()).append(" |\n");
        }
        report.append("\n## Derived freshness (no sleep)\n\n")
            .append("Freshness is conservatively derived as scheduling interval + measured runtime. It is not an observed scheduler distribution. The selected baseline uses activeHours=")
            .append(config.activeHours()).append(" and n=").append(config.runs())
            .append(". With n=5, nearest-rank p95 is approximately the maximum observed runtime.\n\n")
            .append("| Interval | Basis products | Basis chunk | Runtime p50 | Runtime p95 | Runtime max | Freshness p50 | Freshness p95 | Freshness max |\n")
            .append("| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        for (RankingBatchFreshnessResult result : freshness) {
            report.append("| ").append(result.intervalSeconds()).append("s | ").append(result.basisCardinality()).append(" | ")
                .append(result.basisChunkSize()).append(" | ").append(number(result.runtimeP50Ms())).append("ms | ")
                .append(number(result.runtimeP95Ms())).append("ms | ").append(number(result.runtimeMaxMs())).append("ms | ")
                .append(number(result.derivedFreshnessP50Ms())).append("ms | ")
                .append(number(result.derivedFreshnessP95Ms())).append("ms | ")
                .append(number(result.derivedFreshnessMaxMs())).append("ms |\n");
        }
        return report.toString();
    }

    private static String safe(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    record ReportFiles(Path throughputCsv, Path recoveryCsv, Path freshnessCsv, Path markdown) {
        void verifyWritten() {
            if (!Files.isRegularFile(throughputCsv) || !Files.isRegularFile(recoveryCsv)
                || !Files.isRegularFile(freshnessCsv) || !Files.isRegularFile(markdown)) {
                throw new IllegalStateException("ranking batch benchmark report was not written");
            }
        }
    }
}
