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

final class RankingBenchmarkReport {
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private RankingBenchmarkReport() {
    }

    static ReportFiles write(
        RankingBenchmarkConfig config,
        RankingBenchmarkEnvironment environment,
        List<RankingBenchmarkResult> results,
        Instant generatedAt
    )
        throws IOException {
        if (results == null || results.size() != config.expectedResultCount()) {
            throw new IllegalArgumentException("Expected " + config.expectedResultCount() + " results but received "
                + (results == null ? 0 : results.size()));
        }
        Files.createDirectories(config.outputDir());
        String prefix = TIMESTAMP.format(generatedAt.atZone(ZONE)) + "-" + safe(config.label());
        Path csv = config.outputDir().resolve(prefix + "-results.csv");
        Path markdown = config.outputDir().resolve(prefix + "-report.md");
        Files.writeString(csv, csv(config, results), StandardCharsets.UTF_8);
        Files.writeString(markdown, markdown(config, environment, results, generatedAt), StandardCharsets.UTF_8);
        ReportFiles files = new ReportFiles(csv, markdown);
        files.verifyWritten();
        return files;
    }

    private static String csv(RankingBenchmarkConfig config, List<RankingBenchmarkResult> results) {
        StringBuilder output = new StringBuilder("label,boundary,cardinality,page_size,run,requests,successes,failures,elapsed_ms,throughput_rps,avg_ms,p50_ms,p95_ms,p99_ms,max_ms,redis_memory_bytes,redis_bytes_per_member\n");
        for (RankingBenchmarkResult result : results) {
            output.append(csvValue(config.label())).append(',').append(result.boundary()).append(',')
                .append(result.cardinality()).append(',').append(result.pageSize()).append(',').append(result.run()).append(',')
                .append(result.requests()).append(',').append(result.successes()).append(',').append(result.failures()).append(',')
                .append(number(result.elapsedMs())).append(',').append(number(result.latency().throughputRps())).append(',')
                .append(number(result.latency().avgMs())).append(',').append(number(result.latency().p50Ms())).append(',')
                .append(number(result.latency().p95Ms())).append(',').append(number(result.latency().p99Ms())).append(',')
                .append(number(result.latency().maxMs())).append(',').append(result.redisMemoryBytes()).append(',')
                .append(number(result.redisBytesPerMember())).append('\n');
        }
        return output.toString();
    }

    private static String markdown(
        RankingBenchmarkConfig config,
        RankingBenchmarkEnvironment environment,
        List<RankingBenchmarkResult> results,
        Instant generatedAt
    ) {
        StringBuilder output = new StringBuilder("# Ranking API Benchmark\n\n")
            .append("Generated at: ").append(generatedAt.atZone(ZONE)).append("\n\n")
            .append("## Configuration\n\n")
            .append("- Label: ").append(config.label()).append('\n')
            .append("- Java: ").append(environment.javaVersion()).append('\n')
            .append("- OS: ").append(environment.osName()).append(" / ").append(environment.osArchitecture()).append('\n')
            .append("- Available processors: ").append(environment.availableProcessors()).append('\n')
            .append("- Timezone: ").append(environment.timezone()).append('\n')
            .append("- Redis version: ").append(environment.redisVersion()).append('\n')
            .append("- Fixed ranking date: 2026-07-16 (Asia/Seoul)\n")
            .append("- Cardinalities: ").append(config.cardinalities()).append('\n')
            .append("- Page sizes: ").append(config.pageSizes()).append('\n')
            .append("- MySQL products: top ")
            .append(config.pageSizes().stream().mapToInt(Integer::intValue).max().orElseThrow())
            .append(" ZSET members only (the largest queried page)\n")
            .append("- Concurrency / iterations / warmup / runs: ").append(config.concurrency()).append(" / ")
            .append(config.iterations()).append(" / ").append(config.warmup()).append(" / ").append(config.runs()).append("\n\n")
            .append("## Results\n\n")
            .append("| Boundary | Members | Page | Run | OK/Fail | RPS | Avg ms | p50 | p95 | p99 | Max | Redis bytes | Bytes/member |\n")
            .append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        for (RankingBenchmarkResult result : results) {
            output.append("| ").append(result.boundary()).append(" | ").append(result.cardinality()).append(" | ")
                .append(result.pageSize()).append(" | ").append(result.run()).append(" | ").append(result.successes())
                .append('/').append(result.failures()).append(" | ").append(number(result.latency().throughputRps()))
                .append(" | ").append(number(result.latency().avgMs())).append(" | ").append(number(result.latency().p50Ms()))
                .append(" | ").append(number(result.latency().p95Ms())).append(" | ").append(number(result.latency().p99Ms()))
                .append(" | ").append(number(result.latency().maxMs())).append(" | ").append(result.redisMemoryBytes())
                .append(" | ").append(number(result.redisBytesPerMember())).append(" |\n");
        }
        return output.append("\n## Interpretation boundaries\n\n")
            .append("- `redis-repository` isolates ZSET range lookup and Java tuple mapping.\n")
            .append("- `full-http` includes HTTP serialization plus MySQL product/brand aggregation.\n")
            .append("- Redis keeps the full configured cardinality, while MySQL only stores the top maximum-page members. This holds DB table size constant and isolates ZSET cardinality.\n")
            .append("- Values are local same-JVM/Testcontainers comparisons, not production capacity guarantees.\n")
            .append("- The default concurrency is 1 for a cardinality baseline; override it to run a separate load axis.\n")
            .append("- p99 is only a reference when iterations are below 1,000; use more samples before drawing tail-latency conclusions.\n")
            .append("- Redis `MEMORY USAGE` includes the key and allocator overhead; bytes/member is an observed average.\n")
            .toString();
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String safe(String value) {
        return value.trim().replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-|-$", "");
    }

    private static String csvValue(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    record ReportFiles(Path csv, Path markdown) {
        void verifyWritten() {
            if (!Files.isRegularFile(csv) || !Files.isRegularFile(markdown)) {
                throw new IllegalStateException("Ranking benchmark report files were not written");
            }
        }
    }
}
