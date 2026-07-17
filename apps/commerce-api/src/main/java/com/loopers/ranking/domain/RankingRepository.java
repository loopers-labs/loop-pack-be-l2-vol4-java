package com.loopers.ranking.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * 랭킹 ZSET 조회 포트. 구현(infrastructure)이 ZREVRANGE/ZCARD/ZREVRANK 로 읽는다.
 */
public interface RankingRepository {

    /** 해당 날짜 판에서 순위 [start, end] 구간을 내림차순으로 조회한다(0-based, inclusive). */
    List<RankingEntry> range(LocalDate date, long start, long end);

    /** 해당 날짜 판의 전체 상품 수. */
    long size(LocalDate date);

    /** 해당 날짜 판에서 상품의 순위(0-based). 없으면 null. */
    Long rank(LocalDate date, long productId);
}
