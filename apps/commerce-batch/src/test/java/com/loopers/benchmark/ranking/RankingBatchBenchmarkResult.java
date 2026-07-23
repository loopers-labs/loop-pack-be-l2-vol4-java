package com.loopers.benchmark.ranking;

record RankingBatchBenchmarkResult(
    int run,
    int cardinality,
    long metricUnits,
    long sourceRows,
    int activeHours,
    int chunkSize,
    double elapsedMs,
    double metricUnitsPerSecond,
    double productsPerSecond,
    long redisMemberMutations,
    long redisCommandOrChunkCount,
    double totalScore,
    String scoreDigest,
    String status
) {
}
