package com.loopers.domain.ranking;

import java.util.List;

/**
 * MV 적재/정리 포트. 청크 적재(JPA)와 달리 여기 담긴 연산은 전부 집합 단위라 SQL 로 처리하는 편이
 * 자연스럽다(구현체는 JdbcTemplate).
 */
public interface MvProductRankRepository {

    /** 해당 기간 키의 기존 적재분을 모두 지운다 — 같은 파라미터 재실행을 멱등하게 만드는 장치. */
    int deletePeriod(RankingPeriod period, String periodKey);

    /** 점수 내림차순 상위 limit 개의 productId. 동점은 productId 오름차순으로 결정적 순서를 보장한다. */
    List<Long> findTopProductIds(RankingPeriod period, String periodKey, int limit);

    /** 주어진 순서대로 1-based 순위를 부여한다. */
    void assignRanks(RankingPeriod period, String periodKey, List<Long> orderedProductIds);

    /** 순위를 받지 못한(상위 N 밖) 로우를 제거해 MV 를 TOP N 으로 확정한다. */
    int deleteUnranked(RankingPeriod period, String periodKey);
}
