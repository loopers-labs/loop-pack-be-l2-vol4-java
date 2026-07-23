package com.loopers.benchmark.ranking;

record RankingBatchFreshnessResult(
    int intervalSeconds,
    int basisCardinality,
    int basisChunkSize,
    double runtimeP50Ms,
    double runtimeP95Ms,
    double runtimeMaxMs,
    double derivedFreshnessP50Ms,
    double derivedFreshnessP95Ms,
    double derivedFreshnessMaxMs
) {
}
