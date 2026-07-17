package com.loopers.domain.ranking;

public interface RankingRepository {
    void incrementScore(String key, Long productId, double score);
}
