package com.loopers.application.ranking;

public interface RankingScoreWriter {
    void increment(String rankingKey, Long productId, double scoreDelta);
}
