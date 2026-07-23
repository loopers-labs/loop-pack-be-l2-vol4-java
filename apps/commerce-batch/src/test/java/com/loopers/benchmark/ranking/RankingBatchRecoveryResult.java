package com.loopers.benchmark.ranking;

record RankingBatchRecoveryResult(
    int cardinality,
    int chunkSize,
    String baselineDigest,
    String rerunDigest,
    String digestAfterInjectedFailure,
    String digestAfterRetry,
    boolean rerunExact,
    boolean canonicalUnchangedOnFailure,
    boolean retryExact
) {
}
