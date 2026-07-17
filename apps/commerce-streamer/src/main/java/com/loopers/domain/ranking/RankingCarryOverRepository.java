package com.loopers.domain.ranking;

public interface RankingCarryOverRepository {

  RankingCarryOverResult carryOver(
      RankingCarryOverKeys keys, double carryOverRatio, long ttlSeconds);
}
