package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;

/**
 * 기간(주간/월간) 랭킹 조회 포트(week10). 구현은 commerce-batch 가 적재한 Materialized View 를 읽는다.
 *
 * <p><b>왜 {@link RankingRepository} 를 확장하지 않고 포트를 나눴나</b>: 일간은 실시간 ZSET(+스냅샷 폴백)이고
 * 기간은 배치가 확정한 MV 라 <b>소스도 갱신 주기도 다르다</b>. 한 포트에 기간 파라미터를 얹으면 일간에만
 * 의미 있는 {@code findRank}(상품 상세의 실시간 순위)까지 기간 개념을 떠안게 되고, 기존 호출부가 전부 바뀐다.
 *
 * <p>{@code DAILY} 는 이 포트의 대상이 아니다 — {@link RankingPeriod#isDaily()} 로 갈라 {@link RankingRepository}
 * 를 쓴다.
 */
public interface PeriodRankingRepository {

    /**
     * 지정 기간 랭킹의 한 페이지(순위 오름차순)를 반환한다.
     *
     * @param period      WEEKLY / MONTHLY
     * @param periodStart 기간 시작일({@link RankingPeriod#resolveStart})
     * @param page        1부터 시작하는 페이지 번호
     * @param size        페이지 크기
     */
    List<RankedProduct> findPage(RankingPeriod period, LocalDate periodStart, int page, int size);

    /** 지정 기간 MV 에 적재된 상품 수. 페이지네이션 total 로 쓴다(배치 topN 이 상한). */
    long size(RankingPeriod period, LocalDate periodStart);
}
