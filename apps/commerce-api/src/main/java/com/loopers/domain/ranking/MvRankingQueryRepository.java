package com.loopers.domain.ranking;

import java.util.List;

/**
 * 주간/월간 랭킹 MV 조회 포트 — 배치가 미리 계산해 둔 순위를 그대로 읽는다(집계 없음).
 * 적재는 commerce-batch 가 하고, 여기서는 읽기만 한다.
 */
public interface MvRankingQueryRepository {

    /** 해당 기간에 적재된 랭킹 항목 수(= TOP N 확정 개수). */
    long countRanked(RankingPeriod period, String periodKey);

    /** 확정 순위 오름차순으로 offset 부터 size 개. */
    List<RankedProductEntry> findPage(RankingPeriod period, String periodKey, int offset, int size);
}
