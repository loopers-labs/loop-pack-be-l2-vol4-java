package com.loopers.benchmark.ranking;

record RankingRetryDriftBenchmarkResult(
    int batchSize,
    int failurePointPercent,
    int events,
    int products,
    double expectedTotalScore,
    double actualTotalScore,
    double overcountPercent,
    int productsWithDrift,
    double top20OverlapPercent
) {
}
