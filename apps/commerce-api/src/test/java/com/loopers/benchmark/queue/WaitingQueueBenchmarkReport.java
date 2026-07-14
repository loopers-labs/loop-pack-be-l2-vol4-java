package com.loopers.benchmark.queue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class WaitingQueueBenchmarkReport {

    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private WaitingQueueBenchmarkReport() {
    }

    static ReportFiles write(
        WaitingQueueBenchmarkConfig config,
        WaitingQueueBenchmarkEnvironment environment,
        List<WaitingQueueBenchmarkScenario> scenarios,
        Instant generatedAt
    ) throws IOException {
        if (scenarios == null || scenarios.size() != config.expectedScenarioCount()) {
            int actualCount = scenarios == null ? 0 : scenarios.size();
            throw new IllegalArgumentException(
                "Expected " + config.expectedScenarioCount() + " benchmark scenarios but received " + actualCount
            );
        }

        Files.createDirectories(config.outputDir());
        String timestamp = FILE_TIMESTAMP.format(generatedAt.atZone(REPORT_ZONE));
        String prefix = timestamp + "-" + safeFilePart(config.label());
        Path scenariosCsv = config.outputDir().resolve(prefix + "-scenarios.csv");
        Path requestsCsv = config.outputDir().resolve(prefix + "-requests.csv");
        Path markdown = config.outputDir().resolve(prefix + "-report.md");

        Files.writeString(scenariosCsv, scenarioCsv(config, scenarios), StandardCharsets.UTF_8);
        Files.writeString(requestsCsv, requestCsv(config, scenarios), StandardCharsets.UTF_8);
        Files.writeString(markdown, markdown(config, environment, scenarios, generatedAt), StandardCharsets.UTF_8);

        ReportFiles files = new ReportFiles(scenariosCsv, requestsCsv, markdown);
        files.verifyWritten();
        return files;
    }

    private static String scenarioCsv(
        WaitingQueueBenchmarkConfig config,
        List<WaitingQueueBenchmarkScenario> scenarios
    ) {
        StringBuilder csv = new StringBuilder();
        csv.append("label,batch_size,admit_delay_ms,run,users,concurrency,requested_db_pool_size,")
            .append("effective_hikari_max,effective_hikari_min,http_success,http_failure,total_duration_ms,")
            .append("e2e_duration_ms,queue_drain_duration_ms,attempted_throughput_tps,successful_throughput_tps,")
            .append("theoretical_admission_tps,observed_admission_tps,avg_latency_ms,p50_ms,p95_ms,p99_ms,max_ms,")
            .append("remaining_queue,remaining_tokens,")
            .append("persisted_orders,persisted_outbox_events,hikari_max_active,hikari_max_pending\n");
        for (WaitingQueueBenchmarkScenario scenario : scenarios) {
            appendCsvRow(csv, List.of(
                config.label(),
                scenario.batchSize(),
                scenario.admitDelayMs(),
                scenario.run(),
                scenario.users(),
                scenario.concurrency(),
                scenario.requestedDbPoolSize(),
                scenario.effectiveHikariMaxPoolSize(),
                scenario.effectiveHikariMinIdle(),
                scenario.httpSuccess(),
                scenario.httpFailure(),
                number(scenario.totalDurationMs()),
                number(scenario.e2eDurationMs()),
                number(scenario.queueDrainDurationMs()),
                number(scenario.attemptedThroughputTps()),
                number(scenario.successfulThroughputTps()),
                number(scenario.theoreticalAdmissionTps()),
                number(scenario.observedAdmissionTps()),
                number(scenario.avgLatencyMs()),
                number(scenario.p50Ms()),
                number(scenario.p95Ms()),
                number(scenario.p99Ms()),
                number(scenario.maxLatencyMs()),
                scenario.remainingQueue(),
                scenario.remainingTokens(),
                scenario.persistedOrders(),
                scenario.persistedOutboxEvents(),
                scenario.hikariMaxActive(),
                scenario.hikariMaxPending()
            ));
        }
        return csv.toString();
    }

    private static String requestCsv(
        WaitingQueueBenchmarkConfig config,
        List<WaitingQueueBenchmarkScenario> scenarios
    ) {
        StringBuilder csv = new StringBuilder();
        csv.append("label,batch_size,admit_delay_ms,run,user_login_id,http_status,success,latency_ms,failure\n");
        for (WaitingQueueBenchmarkScenario scenario : scenarios) {
            for (WaitingQueueBenchmarkRequest request : scenario.requests()) {
                appendCsvRow(csv, List.of(
                    config.label(),
                    scenario.batchSize(),
                    scenario.admitDelayMs(),
                    scenario.run(),
                    request.userLoginId(),
                    request.httpStatus(),
                    request.success(),
                    number(request.latencyMs()),
                    request.failure()
                ));
            }
        }
        return csv.toString();
    }

    private static String markdown(
        WaitingQueueBenchmarkConfig config,
        WaitingQueueBenchmarkEnvironment environment,
        List<WaitingQueueBenchmarkScenario> scenarios,
        Instant generatedAt
    ) {
        List<AggregateComparison> comparisons = aggregate(scenarios);
        AggregateComparison bestSuccessfulThroughput = comparisons.stream()
            .max(Comparator.comparingDouble(AggregateComparison::successfulThroughputTps))
            .orElseThrow();
        AggregateComparison lowestP95 = comparisons.stream()
            .min(Comparator.comparingDouble(comparison -> comparison.latency().p95Ms()))
            .orElseThrow();

        StringBuilder report = new StringBuilder();
        report.append("# Waiting Queue Capacity Benchmark\n\n")
            .append("Generated at: ").append(generatedAt.atZone(REPORT_ZONE)).append("\n\n")
            .append("## Environment and configuration\n\n")
            .append("| Item | Value |\n")
            .append("| --- | --- |\n")
            .append("| Label | ").append(markdown(config.label())).append(" |\n")
            .append("| Java | ").append(markdown(environment.javaVersion())).append(" |\n")
            .append("| OS | ").append(markdown(environment.osName())).append(" / ")
            .append(markdown(environment.osArchitecture())).append(" |\n")
            .append("| Available processors | ").append(environment.availableProcessors()).append(" |\n")
            .append("| Timezone | ").append(markdown(environment.timezone())).append(" |\n")
            .append("| Spring profile | ").append(markdown(environment.springProfile())).append(" |\n")
            .append("| RANDOM_PORT | ").append(environment.serverPort()).append(" |\n")
            .append("| Users / concurrency | ").append(config.users()).append(" / ")
            .append(config.concurrency()).append(" |\n")
            .append("| Batch sizes | ").append(markdown(config.batchSizes().toString())).append(" |\n")
            .append("| Admit delays (ms) | ").append(markdown(config.admitDelaysMs().toString())).append(" |\n")
            .append("| Runs / warmup users | ").append(config.runs()).append(" / ")
            .append(config.warmupUsers()).append(" |\n")
            .append("| Requested DB pool max | ").append(config.dbPoolSize()).append(" |\n")
            .append("| Effective Hikari max / min | ")
            .append(scenarios.get(0).effectiveHikariMaxPoolSize()).append(" / ")
            .append(scenarios.get(0).effectiveHikariMinIdle()).append(" |\n")
            .append("| Scheduler mode | JUnit pacing harness using real WaitingQueueAdmitService; production scheduler disabled |\n")
            .append("| Workload | One shared high-stock product (intentional hot-row contention) |\n")
            .append("| HTTP latency boundary | Auth DB lookup + BCrypt verification + order transaction + outbox write |\n")
            .append("| Containers | MySQL 8.0; Redis test fixture uses mutable redis:latest |\n\n")
            .append("## Raw scenario results\n\n")
            .append("| Batch | Delay ms | Run | OK | Fail | Total ms | E2E ms | Drain ms | Attempted TPS | Successful TPS | Theoretical admit TPS | Observed admit TPS | Avg | p50 | p95 | p99 | Max | Queue | Tokens | Orders | Outbox | Hikari active/pending |\n")
            .append("| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        for (WaitingQueueBenchmarkScenario scenario : scenarios) {
            report.append("| ").append(scenario.batchSize())
                .append(" | ").append(scenario.admitDelayMs())
                .append(" | ").append(scenario.run())
                .append(" | ").append(scenario.httpSuccess())
                .append(" | ").append(scenario.httpFailure())
                .append(" | ").append(number(scenario.totalDurationMs()))
                .append(" | ").append(number(scenario.e2eDurationMs()))
                .append(" | ").append(number(scenario.queueDrainDurationMs()))
                .append(" | ").append(number(scenario.attemptedThroughputTps()))
                .append(" | ").append(number(scenario.successfulThroughputTps()))
                .append(" | ").append(number(scenario.theoreticalAdmissionTps()))
                .append(" | ").append(number(scenario.observedAdmissionTps()))
                .append(" | ").append(number(scenario.avgLatencyMs()))
                .append(" | ").append(number(scenario.p50Ms()))
                .append(" | ").append(number(scenario.p95Ms()))
                .append(" | ").append(number(scenario.p99Ms()))
                .append(" | ").append(number(scenario.maxLatencyMs()))
                .append(" | ").append(scenario.remainingQueue())
                .append(" | ").append(scenario.remainingTokens())
                .append(" | ").append(scenario.persistedOrders())
                .append(" | ").append(scenario.persistedOutboxEvents())
                .append(" | ").append(scenario.hikariMaxActive()).append(" / ")
                .append(scenario.hikariMaxPending()).append(" |\n");
        }

        report.append("\n## Aggregated batch/delay comparison\n\n")
            .append("Average and percentiles below are calculated from pooled raw request latencies across runs.\n\n")
            .append("| Batch | Delay ms | Runs | OK / Requests | Success % | Attempted TPS | Successful TPS | Theoretical admit TPS | Observed admit TPS | Avg | p50 | p95 | p99 | Max | Avg E2E ms | Orders | Outbox | Max active/pending |\n")
            .append("| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        for (AggregateComparison comparison : comparisons) {
            report.append("| ").append(comparison.key().batchSize())
                .append(" | ").append(comparison.key().admitDelayMs())
                .append(" | ").append(comparison.runs())
                .append(" | ").append(comparison.success()).append(" / ").append(comparison.requests())
                .append(" | ").append(number(comparison.successRate()))
                .append(" | ").append(number(comparison.attemptedThroughputTps()))
                .append(" | ").append(number(comparison.successfulThroughputTps()))
                .append(" | ").append(number(comparison.theoreticalAdmissionTps()))
                .append(" | ").append(number(comparison.observedAdmissionTps()))
                .append(" | ").append(number(comparison.latency().avgMs()))
                .append(" | ").append(number(comparison.latency().p50Ms()))
                .append(" | ").append(number(comparison.latency().p95Ms()))
                .append(" | ").append(number(comparison.latency().p99Ms()))
                .append(" | ").append(number(comparison.latency().maxMs()))
                .append(" | ").append(number(comparison.averageE2eMs()))
                .append(" | ").append(comparison.persistedOrders())
                .append(" | ").append(comparison.persistedOutboxEvents())
                .append(" | ").append(comparison.maxActive()).append(" / ")
                .append(comparison.maxPending()).append(" |\n");
        }

        report.append("\n## Observations\n\n")
            .append("- Best successful throughput: batch ").append(bestSuccessfulThroughput.key().batchSize())
            .append(" / delay ").append(bestSuccessfulThroughput.key().admitDelayMs()).append(" ms (pooled ")
            .append(number(bestSuccessfulThroughput.successfulThroughputTps())).append(" TPS).\n")
            .append("- Lowest p95: batch ").append(lowestP95.key().batchSize())
            .append(" / delay ").append(lowestP95.key().admitDelayMs()).append(" ms (pooled ")
            .append(number(lowestP95.latency().p95Ms())).append(" ms).\n\n")
            .append("## Caveats\n\n")
            .append("- Local same-JVM/Testcontainers values are comparative, not production capacity guarantees.\n")
            .append("- The JUnit harness controls fixed-delay pacing while the production WaitingQueueAdmitScheduler remains disabled.\n")
            .append("- The shared product deliberately serializes product-row updates, so results describe a hot-row order workload.\n")
            .append("- Fast HTTP failures still count toward attempted TPS; use successful TPS, persisted orders/outbox, remaining queue/tokens, and success counts together.\n");
        return report.toString();
    }

    private static List<AggregateComparison> aggregate(List<WaitingQueueBenchmarkScenario> scenarios) {
        Map<ScenarioKey, List<WaitingQueueBenchmarkScenario>> grouped = new LinkedHashMap<>();
        for (WaitingQueueBenchmarkScenario scenario : scenarios) {
            grouped.computeIfAbsent(
                new ScenarioKey(scenario.batchSize(), scenario.admitDelayMs()),
                ignored -> new ArrayList<>()
            ).add(scenario);
        }

        List<AggregateComparison> comparisons = new ArrayList<>();
        for (Map.Entry<ScenarioKey, List<WaitingQueueBenchmarkScenario>> entry : grouped.entrySet()) {
            List<WaitingQueueBenchmarkScenario> runs = entry.getValue();
            List<WaitingQueueBenchmarkRequest> requests = runs.stream()
                .flatMap(run -> run.requests().stream())
                .toList();
            WaitingQueueBenchmarkStatistics.LatencySummary latency = WaitingQueueBenchmarkStatistics.summarize(
                requests.stream().map(WaitingQueueBenchmarkRequest::latencyMs).toList()
            );
            int success = (int) requests.stream().filter(WaitingQueueBenchmarkRequest::success).count();
            double totalE2eMs = runs.stream().mapToDouble(WaitingQueueBenchmarkScenario::e2eDurationMs).sum();
            double totalDrainMs = runs.stream().mapToDouble(WaitingQueueBenchmarkScenario::queueDrainDurationMs).sum();
            long totalUsers = runs.stream().mapToLong(WaitingQueueBenchmarkScenario::users).sum();
            comparisons.add(new AggregateComparison(
                entry.getKey(),
                runs.size(),
                requests.size(),
                success,
                percentage(success, requests.size()),
                ratePerSecond(requests.size(), totalE2eMs),
                ratePerSecond(success, totalE2eMs),
                runs.get(0).theoreticalAdmissionTps(),
                ratePerSecond(totalUsers, totalDrainMs),
                latency,
                totalE2eMs / runs.size(),
                runs.stream().mapToLong(WaitingQueueBenchmarkScenario::persistedOrders).sum(),
                runs.stream().mapToLong(WaitingQueueBenchmarkScenario::persistedOutboxEvents).sum(),
                runs.stream().mapToInt(WaitingQueueBenchmarkScenario::hikariMaxActive).max().orElse(0),
                runs.stream().mapToInt(WaitingQueueBenchmarkScenario::hikariMaxPending).max().orElse(0)
            ));
        }
        return List.copyOf(comparisons);
    }

    private static double ratePerSecond(long count, double durationMs) {
        return durationMs <= 0 ? Double.POSITIVE_INFINITY : count * 1_000.0 / durationMs;
    }

    private static double percentage(long part, long total) {
        return total == 0 ? 0.0 : part * 100.0 / total;
    }

    private static String safeFilePart(String value) {
        String safe = value.trim().replaceAll("[^\\p{L}\\p{N}._-]+", "-");
        return safe.isBlank() ? "benchmark" : safe;
    }

    private static String markdown(String value) {
        return value == null ? "" : value.replace("|", "\\|").replace("\n", " ");
    }

    private static String number(double value) {
        if (Double.isInfinite(value)) {
            return "Infinity";
        }
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static void appendCsvRow(StringBuilder csv, List<?> values) {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                csv.append(',');
            }
            csv.append(csv(values.get(index)));
        }
        csv.append('\n');
    }

    private static String csv(Object value) {
        String text = value == null ? "" : value.toString();
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    record ReportFiles(Path scenariosCsv, Path requestsCsv, Path markdown) {
        void verifyWritten() throws IOException {
            for (Path file : List.of(scenariosCsv, requestsCsv, markdown)) {
                if (!Files.isRegularFile(file) || Files.size(file) == 0) {
                    throw new IOException("Benchmark report was not written: " + file);
                }
            }
        }
    }

    private record ScenarioKey(int batchSize, long admitDelayMs) {
    }

    private record AggregateComparison(
        ScenarioKey key,
        int runs,
        int requests,
        int success,
        double successRate,
        double attemptedThroughputTps,
        double successfulThroughputTps,
        double theoreticalAdmissionTps,
        double observedAdmissionTps,
        WaitingQueueBenchmarkStatistics.LatencySummary latency,
        double averageE2eMs,
        long persistedOrders,
        long persistedOutboxEvents,
        int maxActive,
        int maxPending
    ) {
    }
}
