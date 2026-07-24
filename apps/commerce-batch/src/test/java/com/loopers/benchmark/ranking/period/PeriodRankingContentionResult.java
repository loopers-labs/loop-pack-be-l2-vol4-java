package com.loopers.benchmark.ranking.period;

record PeriodRankingContentionResult(
    int run,
    String isolation,
    long holdMs,
    double snapshotInsertMs,
    double sourceUpdateLatencyMs,
    int updatedRows
) {
}
