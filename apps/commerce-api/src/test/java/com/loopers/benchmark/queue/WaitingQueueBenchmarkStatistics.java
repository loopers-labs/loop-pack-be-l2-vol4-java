package com.loopers.benchmark.queue;

import java.util.List;

final class WaitingQueueBenchmarkStatistics {

    private WaitingQueueBenchmarkStatistics() {
    }

    static LatencySummary summarize(List<Double> latenciesMs) {
        if (latenciesMs == null || latenciesMs.isEmpty()) {
            throw new IllegalArgumentException("latency measurements must not be empty");
        }
        if (latenciesMs.stream().anyMatch(value -> value == null || value < 0 || !Double.isFinite(value))) {
            throw new IllegalArgumentException("latency measurements must be finite non-negative values");
        }
        List<Double> sorted = latenciesMs.stream().sorted().toList();
        return new LatencySummary(
            sorted.stream().mapToDouble(Double::doubleValue).average().orElseThrow(),
            nearestRank(sorted, 50),
            nearestRank(sorted, 95),
            nearestRank(sorted, 99),
            sorted.get(sorted.size() - 1)
        );
    }

    private static double nearestRank(List<Double> sortedValues, int percentile) {
        int rank = (int) Math.ceil(percentile / 100.0 * sortedValues.size());
        return sortedValues.get(Math.max(0, rank - 1));
    }

    record LatencySummary(double avgMs, double p50Ms, double p95Ms, double p99Ms, double maxMs) {
    }
}
