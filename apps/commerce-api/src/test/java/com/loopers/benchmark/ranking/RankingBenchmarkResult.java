package com.loopers.benchmark.ranking;

record RankingBenchmarkResult(
    String boundary,
    int cardinality,
    int pageSize,
    int run,
    int requests,
    int successes,
    int failures,
    double elapsedMs,
    RankingBenchmarkStatistics.Summary latency,
    long redisMemoryBytes,
    double redisBytesPerMember
) {
}
