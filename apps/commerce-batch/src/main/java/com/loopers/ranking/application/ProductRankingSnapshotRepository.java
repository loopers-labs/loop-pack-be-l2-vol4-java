package com.loopers.ranking.application;

import java.time.Instant;
import java.util.Optional;

public interface ProductRankingSnapshotRepository {

    Optional<ProductRankingSnapshotHeader> findBy(ProductRankingSnapshotKey key);

    Optional<ProductRankingSnapshotHeader> findById(long snapshotId);

    boolean existsNewerCompletedThan(ProductRankingSnapshotKey key);

    void insert(NewProductRankingSnapshot snapshot);

    boolean completeIfIncomplete(long snapshotId, Instant completedAt);
}
