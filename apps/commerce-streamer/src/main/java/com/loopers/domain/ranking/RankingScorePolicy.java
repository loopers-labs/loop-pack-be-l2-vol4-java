package com.loopers.domain.ranking;

/**
 * 랭킹 점수 정책(VO). 유저 행동 신호를 ZINCRBY 델타(점수)로 환산하는 규칙을 한곳에 캡슐화한다.
 * 가중치는 "주문 1건 > 좋아요 3건" 같은 검증 가능한 비즈니스 제약을 인코딩한다.
 * (초기엔 상수 → 이후 config 외부화 가능. guide 결정 #2)
 */
public class RankingScorePolicy {

    // 신호별 가중치. 의도: 구매 결정에 가까운 신호일수록 크게 반영한다.
    private static final double VIEW_WEIGHT = 0.1;
    private static final double LIKE_WEIGHT = 0.2;
    private static final double ORDER_WEIGHT = 0.7;

    /**
     * 한 이벤트가 랭킹 점수에 더하는 델타를 반환한다.
     * - 조회: 고정 가중치
     * - 좋아요/취소: 방향(±) 가중치
     * - 주문: 가중치 × 금액 정규화(log10(1 + lineAmount)) — 스케일 폭주 억제, 음수/정의역(0) 차단
     * <p>
     * lineAmount 는 ORDER 신호에만 의미가 있다(그 외 신호에서는 무시).
     */
    public double scoreFor(RankingSignal signal, long lineAmount) {
        return switch (signal) {
            case VIEW -> VIEW_WEIGHT;
            case LIKE -> LIKE_WEIGHT;
            case UNLIKE -> -LIKE_WEIGHT;
            case ORDER -> ORDER_WEIGHT * Math.log10(1 + lineAmount);
        };
    }
}
