package com.loopers.benchmark.ranking;

import java.util.List;

final class RankingBatchStatistics {
    private RankingBatchStatistics() {
    }

    static double percentile(List<Double> values, int percentile) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("measurements must not be empty");
        }
        if (percentile < 1 || percentile > 100) {
            throw new IllegalArgumentException("percentile must be between 1 and 100");
        }
        List<Double> sorted = values.stream().sorted().toList();
        int rank = (int) Math.ceil(percentile / 100.0 * sorted.size());
        return sorted.get(Math.max(0, rank - 1));
    }
}
