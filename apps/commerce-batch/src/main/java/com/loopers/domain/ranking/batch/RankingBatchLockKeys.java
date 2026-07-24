package com.loopers.domain.ranking.batch;

public final class RankingBatchLockKeys {

    private static final String PREFIX = "ranking:batch:lock:";

    private RankingBatchLockKeys() {
    }

    public static String of(String period, String periodKey) {
        return PREFIX + period + ":" + periodKey;
    }
}