package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RankingQueryRepository {

    /** 점수 내림차순으로 offset 부터 size 개 조회 (ZREVRANGE WITHSCORES). */
    List<RankedProductEntry> findPage(LocalDate date, int offset, int size);

    /** 0-based 내림차순 순위 (ZREVRANK). 순위 밖이면 empty. */
    Optional<Long> findRank(LocalDate date, Long productId);

    /** 해당 일자 랭킹 멤버 수 (ZCARD). */
    long countRanked(LocalDate date);
}
