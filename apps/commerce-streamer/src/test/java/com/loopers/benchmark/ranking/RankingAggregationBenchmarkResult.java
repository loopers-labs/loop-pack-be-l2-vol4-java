package com.loopers.benchmark.ranking;

record RankingAggregationBenchmarkResult(
    String distribution,
    int batchSize,
    int run,
    int events,
    int cardinality,
    long redisUpdates,
    double elapsedMs,
    double eventsPerSecond,
    double updatesPerEvent,
    double scorePerEvent,
    RankingBenchmarkStatistics.LatencySummary batchLatency,
    double finalScore
) {
}
