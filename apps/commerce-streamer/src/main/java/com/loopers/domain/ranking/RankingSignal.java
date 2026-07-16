package com.loopers.domain.ranking;

/**
 * 랭킹 점수에 반영되는 유저 행동 신호의 종류. 랭킹 컨텍스트가 스스로 정의하는 어휘로,
 * metrics 의 CatalogEventType 에 얽매이지 않는다(바운디드 컨텍스트 독립).
 */
public enum RankingSignal {
    VIEW,
    LIKE,
    UNLIKE,
    ORDER
}
