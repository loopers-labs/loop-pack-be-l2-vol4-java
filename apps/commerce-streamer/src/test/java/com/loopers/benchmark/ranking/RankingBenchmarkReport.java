package com.loopers.benchmark.ranking;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class RankingBenchmarkReport {
    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private RankingBenchmarkReport() {
    }

    static ReportFiles write(
        RankingBenchmarkConfig config,
        List<RankingAggregationBenchmarkResult> aggregationResults,
        List<RankingRetryDriftBenchmarkResult> retryResults,
        Instant generatedAt
    ) throws IOException {
        if (aggregationResults == null || aggregationResults.isEmpty() || retryResults == null || retryResults.isEmpty()) {
            throw new IllegalArgumentException("benchmark results must not be empty");
        }
        Files.createDirectories(config.outputDir());
        String suffix = generatedAt.atZone(REPORT_ZONE).format(FILE_TIME) + "-" + safe(config.label());
        Path aggregationCsv = config.outputDir().resolve("ranking-aggregation-" + suffix + ".csv");
        Path retryCsv = config.outputDir().resolve("ranking-retry-drift-" + suffix + ".csv");
        Path markdown = config.outputDir().resolve("ranking-streamer-" + suffix + ".md");
        Files.writeString(aggregationCsv, aggregationCsv(config, aggregationResults), StandardCharsets.UTF_8);
        Files.writeString(retryCsv, retryCsv(config, retryResults), StandardCharsets.UTF_8);
        Files.writeString(markdown, markdown(config, aggregationResults, retryResults, generatedAt), StandardCharsets.UTF_8);
        return new ReportFiles(aggregationCsv, retryCsv, markdown);
    }

    private static String aggregationCsv(RankingBenchmarkConfig config, List<RankingAggregationBenchmarkResult> results) {
        StringBuilder csv = new StringBuilder("label,distribution,batch_size,run,events,cardinality,elapsed_ms,events_per_second,redis_updates,updates_per_event,batch_avg_ms,batch_p50_ms,batch_p95_ms,batch_p99_ms,final_score\n");
        for (RankingAggregationBenchmarkResult result : results) {
            csv.append(config.label()).append(',').append(result.distribution()).append(',')
                .append(result.batchSize()).append(',').append(result.run()).append(',').append(result.events()).append(',')
                .append(result.cardinality()).append(',').append(number(result.elapsedMs())).append(',')
                .append(number(result.eventsPerSecond())).append(',').append(result.redisUpdates()).append(',')
                .append(number(result.updatesPerEvent())).append(',').append(number(result.batchLatency().averageMs())).append(',')
                .append(number(result.batchLatency().p50Ms())).append(',').append(number(result.batchLatency().p95Ms())).append(',')
                .append(number(result.batchLatency().p99Ms())).append(',').append(number(result.finalScore())).append('\n');
        }
        return csv.toString();
    }

    private static String retryCsv(RankingBenchmarkConfig config, List<RankingRetryDriftBenchmarkResult> results) {
        StringBuilder csv = new StringBuilder("label,batch_size,failure_point_percent,events,products,expected_total_score,actual_total_score,overcount_percent,products_with_drift,top20_overlap_percent\n");
        for (RankingRetryDriftBenchmarkResult result : results) {
            csv.append(config.label()).append(',').append(result.batchSize()).append(',')
                .append(result.failurePointPercent()).append(',').append(result.events()).append(',')
                .append(result.products()).append(',').append(number(result.expectedTotalScore())).append(',')
                .append(number(result.actualTotalScore())).append(',').append(number(result.overcountPercent())).append(',')
                .append(result.productsWithDrift()).append(',').append(number(result.top20OverlapPercent())).append('\n');
        }
        return csv.toString();
    }

    private static String markdown(
        RankingBenchmarkConfig config,
        List<RankingAggregationBenchmarkResult> aggregationResults,
        List<RankingRetryDriftBenchmarkResult> retryResults,
        Instant generatedAt
    ) {
        StringBuilder report = new StringBuilder("# Ranking Streamer Benchmark\n\n")
            .append("Generated at: ").append(generatedAt.atZone(REPORT_ZONE)).append("\n\n")
            .append("## Environment\n\n")
            .append("| Item | Value |\n| --- | --- |\n")
            .append("| Label | `").append(config.label()).append("` |\n")
            .append("| Java | ").append(System.getProperty("java.version")).append(" |\n")
            .append("| OS | ").append(System.getProperty("os.name")).append(" / ")
            .append(System.getProperty("os.arch")).append(" |\n")
            .append("| Available processors | ").append(Runtime.getRuntime().availableProcessors()).append(" |\n")
            .append("| Timezone | ").append(System.getProperty("user.timezone")).append(" |\n")
            .append("| Redis test image | `redis:latest` (mutable test-fixture image; record resolved version externally when comparing runs) |\n")
            .append("| Kafka production context | max.poll.records=3000, concurrency=3, fetch.min.bytes=1 MiB, fetch.max.wait.ms=5000 |\n\n")
            .append("## Claim boundary\n\n")
            .append("This is a processor-to-real-Redis benchmark. It does not include Kafka broker, fetch wait, deserialization, consumer scheduling, manual ACK, or lag drain time, so events/s is not Kafka E2E throughput. Production consumer settings above are context only.\n\n")
            .append("## Redis aggregation throughput\n\n")
            .append("Actual Redis Lua updates run through `RedisRankingScoreWriter`. Setup, Redis cleanup, workload creation, correctness checks, and report I/O are outside the timer. Batch latency measures one `CatalogRankingEventProcessor.process` call. Warmup primes JIT, Lettuce, and Lua before measured scenarios; batch order rotates between runs.\n\n")
            .append("| Distribution | Batch | Run | Events | Cardinality | Redis updates | Updates/event | Elapsed ms | Events/s | p50 ms | p95 ms | p99 ms | Final score |\n")
            .append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        for (RankingAggregationBenchmarkResult result : aggregationResults) {
            report.append("| ").append(result.distribution()).append(" | ").append(result.batchSize()).append(" | ")
                .append(result.run()).append(" | ").append(result.events()).append(" | ").append(result.cardinality())
                .append(" | ").append(result.redisUpdates()).append(" | ").append(number(result.updatesPerEvent()))
                .append(" | ").append(number(result.elapsedMs())).append(" | ").append(number(result.eventsPerSecond()))
                .append(" | ").append(number(result.batchLatency().p50Ms())).append(" | ")
                .append(number(result.batchLatency().p95Ms())).append(" | ").append(number(result.batchLatency().p99Ms()))
                .append(" | ").append(number(result.finalScore())).append(" |\n");
        }
        report.append("\n## Aggregated comparison\n\n")
            .append("| Distribution | Batch | Runs | Avg events/s | Median events/s | Avg updates/event |\n")
            .append("| --- | ---: | ---: | ---: | ---: | ---: |\n");
        Map<String, List<RankingAggregationBenchmarkResult>> grouped = new LinkedHashMap<>();
        aggregationResults.forEach(result -> grouped.computeIfAbsent(
            result.distribution() + ":" + result.batchSize(), ignored -> new java.util.ArrayList<>()
        ).add(result));
        grouped.values().forEach(runs -> {
            RankingAggregationBenchmarkResult first = runs.get(0);
            List<Double> throughputs = runs.stream().map(RankingAggregationBenchmarkResult::eventsPerSecond).sorted().toList();
            report.append("| ").append(first.distribution()).append(" | ").append(first.batchSize()).append(" | ")
                .append(runs.size()).append(" | ").append(number(throughputs.stream().mapToDouble(Double::doubleValue).average().orElseThrow()))
                .append(" | ").append(number(median(throughputs))).append(" | ")
                .append(number(runs.stream().mapToDouble(RankingAggregationBenchmarkResult::updatesPerEvent).average().orElseThrow()))
                .append(" |\n");
        });
        report.append("\n## At-least-once retry drift\n\n")
            .append("One batch fails after the configured share of aggregate writes, then the same batch is retried in full.\n\n")
            .append("| Batch | Failure point | Events | Products | Expected score | Actual score | Overcount | Drift products | Top 20 overlap |\n")
            .append("| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        for (RankingRetryDriftBenchmarkResult result : retryResults) {
            report.append("| ").append(result.batchSize()).append(" | ").append(result.failurePointPercent()).append("% | ")
                .append(result.events()).append(" | ").append(result.products()).append(" | ")
                .append(number(result.expectedTotalScore())).append(" | ").append(number(result.actualTotalScore()))
                .append(" | ").append(number(result.overcountPercent())).append("% | ")
                .append(result.productsWithDrift()).append(" | ").append(number(result.top20OverlapPercent())).append("% |\n");
        }
        return report.append("\n## Limits\n\n")
            .append("- Local Testcontainers figures are comparative measurements, not production capacity guarantees.\n")
            .append("- Retry drift uses a deterministic in-memory writer to isolate delivery semantics from Redis latency.\n")
            .append("- p99 based on fewer than 1,000 measured batches is a reference value, not a stable tail-latency estimate.\n")
            .toString();
    }

    private static double median(List<Double> sorted) {
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 0 ? (sorted.get(middle - 1) + sorted.get(middle)) / 2.0 : sorted.get(middle);
    }

    private static String safe(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    record ReportFiles(Path aggregationCsv, Path retryCsv, Path markdown) {
        void verifyWritten() {
            if (!Files.isRegularFile(aggregationCsv) || !Files.isRegularFile(retryCsv) || !Files.isRegularFile(markdown)) {
                throw new IllegalStateException("ranking benchmark report was not written");
            }
        }
    }
}
