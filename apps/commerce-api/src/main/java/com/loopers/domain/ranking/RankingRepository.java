package com.loopers.domain.ranking;

import java.util.List;
import java.util.Optional;

public interface RankingRepository {
    List<RankingEntry> getTopN(String key, long offset, long limit);
    Optional<Long> getRank(String key, Long productId);
    long getTotalCount(String key);

    record RankingEntry(Long productId, double score) {}
}
