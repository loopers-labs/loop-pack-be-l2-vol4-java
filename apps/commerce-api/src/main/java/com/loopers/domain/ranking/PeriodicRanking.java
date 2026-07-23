package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;

public interface PeriodicRanking {

    /** date 가 속한 기간 랭킹의 [page*size, page*size+size-1] 구간을 순위 오름차순으로 반환한다. */
    List<PeriodRankedProduct> page(RankingPeriod period, LocalDate date, int page, int size);

    /** date 가 속한 기간 랭킹에 적재된 전체 상품 수(페이지 total). */
    long totalCount(RankingPeriod period, LocalDate date);
}