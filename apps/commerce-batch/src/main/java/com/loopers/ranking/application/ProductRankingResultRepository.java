package com.loopers.ranking.application;

import com.loopers.ranking.RankingPeriod;

import java.util.List;

public interface ProductRankingResultRepository {

    void insertAll(
        RankingPeriod period,
        long snapshotId,
        List<ProductRankingAssignment> rankings
    );

    List<ProductRankingAssignment> findPublishedRankings(
        RankingPeriod period,
        long snapshotId,
        int limit
    );
}
