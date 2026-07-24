package com.loopers.ranking.application;

import java.util.List;

public interface ProductRankingCandidateRepository {

    void upsertAll(List<? extends RankingCandidate> candidates);

    long countCandidates(long snapshotId);

    List<RankingCandidate> findTopCandidates(
        long snapshotId,
        int limit
    );

    int deleteCandidates(long snapshotId, int limit);
}
