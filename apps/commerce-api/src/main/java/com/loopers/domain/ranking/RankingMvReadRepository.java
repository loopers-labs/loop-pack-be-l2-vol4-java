package com.loopers.domain.ranking;

import java.util.List;
import java.util.Optional;

/**
 * 주간/월간 랭킹 MV(mv_product_rank_weekly/monthly) 조회 포트. commerce-batch가 채운
 * MV를 읽기만 한다 — 배치와 스키마는 같지만 모듈 경계상 별도 엔티티/구현으로 둔다(§3.1).
 */
public interface RankingMvReadRepository {

    List<RankingItem> findPage(RankingMvPeriod period, String periodKey, int page, int size);

    Optional<Long> findRank(RankingMvPeriod period, String periodKey, Long productId);

    long countTotal(RankingMvPeriod period, String periodKey);
}
