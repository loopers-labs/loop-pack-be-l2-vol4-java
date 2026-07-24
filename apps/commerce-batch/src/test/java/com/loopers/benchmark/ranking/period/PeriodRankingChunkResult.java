package com.loopers.benchmark.ranking.period;

record PeriodRankingChunkResult(
    int run,
    int cardinality,
    int chunkSize,
    double elapsedMs,
    double productsPerSecond,
    int stagingPageCount,
    String digest
) {
}
