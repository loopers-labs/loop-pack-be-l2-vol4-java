package com.loopers.benchmark.ranking;

import java.util.List;

final class RankingBenchmarkStatistics {
    private RankingBenchmarkStatistics() {
    }

    static LatencySummary summarize(List<Double> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("latency measurements must not be empty");
        }
        if (values.stream().anyMatch(value -> value == null || value < 0 || !Double.isFinite(value))) {
            throw new IllegalArgumentException("latency measurements must be finite non-negative values");
        }
        List<Double> sorted = values.stream().sorted().toList();
        return new LatencySummary(
            sorted.stream().mapToDouble(Double::doubleValue).average().orElseThrow(),
            nearestRank(sorted, 50), nearestRank(sorted, 95), nearestRank(sorted, 99)
        );
    }

    private static double nearestRank(List<Double> sorted, int percentile) {
        int rank = (int) Math.ceil(percentile / 100.0 * sorted.size());
        return sorted.get(Math.max(rank - 1, 0));
    }

    record LatencySummary(double averageMs, double p50Ms, double p95Ms, double p99Ms) {
    }
}
