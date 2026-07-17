package com.loopers.benchmark.ranking;

import java.util.List;

final class RankingBenchmarkStatistics {
    private RankingBenchmarkStatistics() {
    }

    static Summary summarize(List<Double> latenciesMs, double elapsedMs) {
        if (latenciesMs == null || latenciesMs.isEmpty()) {
            throw new IllegalArgumentException("latency measurements must not be empty");
        }
        if (latenciesMs.stream().anyMatch(value -> value == null || value < 0 || !Double.isFinite(value))) {
            throw new IllegalArgumentException("latency measurements must be finite non-negative values");
        }
        if (elapsedMs <= 0 || !Double.isFinite(elapsedMs)) {
            throw new IllegalArgumentException("elapsed time must be a finite positive value");
        }
        List<Double> sorted = latenciesMs.stream().sorted().toList();
        return new Summary(
            sorted.stream().mapToDouble(Double::doubleValue).average().orElseThrow(),
            nearestRank(sorted, 50), nearestRank(sorted, 95), nearestRank(sorted, 99),
            sorted.get(sorted.size() - 1), latenciesMs.size() * 1000.0 / elapsedMs
        );
    }

    private static double nearestRank(List<Double> values, int percentile) {
        int rank = (int) Math.ceil(percentile / 100.0 * values.size());
        return values.get(Math.max(0, rank - 1));
    }

    record Summary(double avgMs, double p50Ms, double p95Ms, double p99Ms, double maxMs, double throughputRps) {
    }
}
