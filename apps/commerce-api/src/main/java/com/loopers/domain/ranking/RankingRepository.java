package com.loopers.domain.ranking;

import java.util.List;
import java.util.Optional;

/**
 * 랭킹판(일별 ZSET) 읽기 포트. 정렬·순위 같은 set-level 규칙을 이 어휘로 흡수하고,
 * 어댑터(infrastructure)가 ZREVRANGE/ZREVRANK/ZCARD 로 역전 구현한다.
 */
public interface RankingRepository {

    /**
     * 점수 내림차순 상위 구간을 (productId, score) 로 반환한다(ZREVRANGE WITHSCORES). page 는 0-based.
     */
    List<RankingEntry> topN(RankingKey key, int page, int size);

    /**
     * 상품의 1-based 순위를 반환한다(ZREVRANK+1). 랭킹 밖이면 Optional.empty.
     */
    Optional<Rank> rankOf(RankingKey key, long productId);

    /**
     * 랭킹판에 진입한 상품 수(ZCARD).
     */
    long size(RankingKey key);
}
