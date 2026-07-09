package com.loopers.benchmark.queue;

import java.util.List;

record WaitingQueueBenchmarkRequest(
    String userLoginId,
    int httpStatus,
    boolean success,
    double latencyMs,
    String failure
) {
    WaitingQueueBenchmarkRequest {
        if (userLoginId == null || userLoginId.isBlank()) {
            throw new IllegalArgumentException("userLoginId must not be blank");
        }
        if (latencyMs < 0 || !Double.isFinite(latencyMs)) {
            throw new IllegalArgumentException("latencyMs must be a finite non-negative value");
        }
        failure = failure == null ? "" : failure;
    }
}

record WaitingQueueBenchmarkScenario(
    int batchSize,
    long admitDelayMs,
    int run,
    int users,
    int concurrency,
    int requestedDbPoolSize,
    int effectiveHikariMaxPoolSize,
    int effectiveHikariMinIdle,
    int httpSuccess,
    int httpFailure,
    double totalDurationMs,
    double e2eDurationMs,
    double queueDrainDurationMs,
    double attemptedThroughputTps,
    double successfulThroughputTps,
    double theoreticalAdmissionTps,
    double observedAdmissionTps,
    double avgLatencyMs,
    double p50Ms,
    double p95Ms,
    double p99Ms,
    double maxLatencyMs,
    long remainingQueue,
    long remainingTokens,
    long persistedOrders,
    long persistedOutboxEvents,
    int hikariMaxActive,
    int hikariMaxPending,
    List<WaitingQueueBenchmarkRequest> requests
) {
    WaitingQueueBenchmarkScenario {
        requests = List.copyOf(requests);
        if (requests.size() != users) {
            throw new IllegalArgumentException("request measurement count must match configured users");
        }
        if (httpSuccess + httpFailure != requests.size()) {
            throw new IllegalArgumentException("HTTP outcome count must match request measurements");
        }
    }

    static WaitingQueueBenchmarkScenario completed(
        int batchSize,
        long admitDelayMs,
        int run,
        int users,
        int concurrency,
        int requestedDbPoolSize,
        int effectiveHikariMaxPoolSize,
        int effectiveHikariMinIdle,
        double totalDurationMs,
        double e2eDurationMs,
        double queueDrainDurationMs,
        long remainingQueue,
        long remainingTokens,
        long persistedOrders,
        long persistedOutboxEvents,
        int hikariMaxActive,
        int hikariMaxPending,
        List<WaitingQueueBenchmarkRequest> requests
    ) {
        int success = (int) requests.stream().filter(WaitingQueueBenchmarkRequest::success).count();
        WaitingQueueBenchmarkStatistics.LatencySummary latency = WaitingQueueBenchmarkStatistics.summarize(
            requests.stream().map(WaitingQueueBenchmarkRequest::latencyMs).toList()
        );
        return new WaitingQueueBenchmarkScenario(
            batchSize,
            admitDelayMs,
            run,
            users,
            concurrency,
            requestedDbPoolSize,
            effectiveHikariMaxPoolSize,
            effectiveHikariMinIdle,
            success,
            requests.size() - success,
            totalDurationMs,
            e2eDurationMs,
            queueDrainDurationMs,
            ratePerSecond(requests.size(), e2eDurationMs),
            ratePerSecond(success, e2eDurationMs),
            admitDelayMs == 0 ? Double.POSITIVE_INFINITY : batchSize * 1_000.0 / admitDelayMs,
            ratePerSecond(users, queueDrainDurationMs),
            latency.avgMs(),
            latency.p50Ms(),
            latency.p95Ms(),
            latency.p99Ms(),
            latency.maxMs(),
            remainingQueue,
            remainingTokens,
            persistedOrders,
            persistedOutboxEvents,
            hikariMaxActive,
            hikariMaxPending,
            requests
        );
    }

    private static double ratePerSecond(long count, double durationMs) {
        return durationMs <= 0 ? Double.POSITIVE_INFINITY : count * 1_000.0 / durationMs;
    }
}

record WaitingQueueBenchmarkEnvironment(
    String javaVersion,
    String osName,
    String osArchitecture,
    int availableProcessors,
    String timezone,
    String springProfile,
    int serverPort
) {
    static WaitingQueueBenchmarkEnvironment capture(int serverPort) {
        return new WaitingQueueBenchmarkEnvironment(
            System.getProperty("java.version", "unknown"),
            System.getProperty("os.name", "unknown"),
            System.getProperty("os.arch", "unknown"),
            Runtime.getRuntime().availableProcessors(),
            System.getProperty("user.timezone", "unknown"),
            System.getProperty("spring.profiles.active", "unknown"),
            serverPort
        );
    }
}
