package com.loopers.benchmark.ranking.period;

record PeriodRankingStrategyResult(
    int run,
    String strategy,
    int cardinality,
    long sourceRows,
    int periodDays,
    int activeHours,
    int pageSize,
    double elapsedMs,
    double productsPerSecond,
    int sourceGroupQueryCount,
    int stagingPageCount,
    long stagingRows,
    String digest
) {
}
