package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;

/**
 * 일간 랭킹 ZSET 에 대한 도메인 계약.
 * 구현체는 infrastructure 에서 Redis ZSET(ZINCRBY/ZREVRANGE/ZREVRANK)으로 위임한다.
 */
public interface RankingRepository {

    /** 해당 날짜 랭킹 키에 productId 점수를 delta 만큼 누적(ZINCRBY)하고 TTL 을 보장한다. */
    void incrScore(LocalDate date, Long productId, double delta);

    /** 점수 내림차순 Top-N(ZREVRANGE). offset 은 0-based, count 개를 반환한다. */
    List<RankedProduct> topN(LocalDate date, long offset, long count);

    /** 특정 상품의 순위(ZREVRANK). 랭킹에 없으면 null, 있으면 1-based 순위로 변환해 반환한다. */
    Long findRank(LocalDate date, Long productId);
}
