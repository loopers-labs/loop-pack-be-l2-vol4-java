package com.loopers.ranking.domain;

import java.util.List;

/**
 * 주간·월간 MV 조회 포트. batch 가 만든 mv_product_rank_* 를 읽기만 한다.
 * Redis 와 성질이 달라 {@link RankingRepository} 와 나눈다 — 이쪽은 영속이라 폴백이 없고, 없으면 그냥 없다.
 */
public interface RankingMvRepository {

    /** 그 기간의 랭킹을 인덱스 순서(score DESC, product_id ASC)대로 offset 부터 limit 만큼 조회한다. */
    List<RankingEntry> findPage(RankingPeriod period, String periodKey, long offset, int limit);

    /** 그 기간에 적재된 전체 개수. */
    long count(RankingPeriod period, String periodKey);
}
