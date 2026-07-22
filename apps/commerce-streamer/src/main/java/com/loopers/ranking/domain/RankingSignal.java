package com.loopers.ranking.domain;

/**
 * 랭킹 점수에 기여하는 시그널. delta 는 조회=+1, 좋아요=±1(취소는 -1), 주문=수량.
 */
public enum RankingSignal {
    VIEW,
    LIKE,
    ORDER
}
