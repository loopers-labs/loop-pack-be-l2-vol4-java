package com.loopers.ranking.application;

import com.loopers.ranking.RankingScorePolicy;

import java.time.Instant;
import java.util.Objects;

public record NewProductRankingSnapshot(
    ProductRankingSnapshotKey key,
    RankingScorePolicy scorePolicy,
    Instant createdAt
) {

    public NewProductRankingSnapshot {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(scorePolicy, "scorePolicy must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
