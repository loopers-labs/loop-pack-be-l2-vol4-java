package com.loopers.application.ranking;

/**
 * 이벤트별 랭킹 가산 스코어 정책. 최종 가산치 = {@code Weight × Score} (과제 예시 기준).
 *
 * <pre>
 *   조회   : Weight = 0.1 , Score = 1                 → +0.1
 *   좋아요 : Weight = 0.2 , Score = delta(±1)         → ±0.2  (좋아요 취소는 감점)
 *   주문   : Weight = 0.6 , Score = log10(1 + 매출)   → 매출(price×amount) 정규화
 * </pre>
 *
 * <p><b>매출 정규화(log)</b>: {@code price×amount} 는 값 폭이 매우 커서 그대로 쓰면 고가 상품 1건이 조회/좋아요
 * 수천 건을 압도한다. {@code log10} 으로 상한을 눌러 세 신호의 스케일을 맞춘다. 가중치는 튜닝 여지가 있어
 * 상수로 노출한다(추후 application.yml 로 외부화 가능).
 */
public final class RankingScorePolicy {

    public static final double VIEW_WEIGHT = 0.1;
    public static final double LIKE_WEIGHT = 0.2;
    public static final double ORDER_WEIGHT = 0.6;

    private RankingScorePolicy() {
    }

    /** 조회 1건 = +0.1. */
    public static double viewScore() {
        return VIEW_WEIGHT;
    }

    /** 좋아요 델타(+1/-1)에 비례. 취소(-1)면 감점되어 랭킹이 자연스럽게 내려간다. */
    public static double likeScore(long delta) {
        return LIKE_WEIGHT * delta;
    }

    /** 주문 매출(단가×수량)을 log10 정규화해 가산. 매출이 0 이하이면 가산하지 않는다. */
    public static double orderScore(long unitPrice, long quantity) {
        double revenue = (double) unitPrice * quantity;
        if (revenue <= 0) {
            return 0.0;
        }
        return ORDER_WEIGHT * Math.log10(1 + revenue);
    }
}
