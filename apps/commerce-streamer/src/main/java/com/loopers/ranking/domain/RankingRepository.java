package com.loopers.ranking.domain;

import java.util.List;

/**
 * 랭킹 ZSET 적재 포트. 구현(infrastructure)이 pipeline 으로 한 번에 ZINCRBY 한다.
 */
public interface RankingRepository {
    void incrBy(List<RankingScoreDelta> deltas);
}
