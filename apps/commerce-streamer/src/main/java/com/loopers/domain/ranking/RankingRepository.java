package com.loopers.domain.ranking;

/**
 * 랭킹판(일별 ZSET) 쓰기 포트. 정렬·순위 같은 set-level 규칙은 이 어휘로 흡수하고,
 * 어댑터(infrastructure)가 ZINCRBY 로 역전 구현한다.
 */
public interface RankingRepository {

    /**
     * 주어진 랭킹 키의 상품 점수에 델타를 누적한다(ZINCRBY). 최초 쓰기 시 키에 TTL 을 건다.
     */
    void incrementScore(RankingKey key, long productId, double delta);
}
