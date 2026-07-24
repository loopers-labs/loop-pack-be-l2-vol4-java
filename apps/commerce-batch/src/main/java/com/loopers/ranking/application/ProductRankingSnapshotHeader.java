package com.loopers.ranking.application;

import com.loopers.ranking.RankingScorePolicy;

import java.time.Instant;
import java.util.Objects;

public record ProductRankingSnapshotHeader(
    long id,
    ProductRankingSnapshotKey key,
    RankingScorePolicy scorePolicy,
    Instant createdAt,
    Instant completedAt
) {

    public ProductRankingSnapshotHeader {
        if (id < 1) {
            throw new IllegalArgumentException("id must be positive");
        }
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(scorePolicy, "scorePolicy must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public boolean isCompleted() {
        return completedAt != null;
    }
}
