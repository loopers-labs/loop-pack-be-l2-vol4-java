package com.loopers.domain.ranking;

import java.util.List;

/**
 * 주간·월간 랭킹 MV 조회 포트. 일간(Redis)과 동일하게 순위 순 productId 목록과 전체 수를 돌려줘,
 * RankingFacade의 상품정보 합성 로직을 그대로 재사용한다.
 */
public interface MvRankingRepository {

    /** rank_no 오름차순(=점수 내림차순) productId 목록. offset부터 size개. */
    List<Long> topProductIds(RankingPeriod period, String periodKey, long offset, int size);

    /** 해당 주기에 적재된 전체 상품 수. */
    long size(RankingPeriod period, String periodKey);
}
