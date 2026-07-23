package com.loopers.domain.ranking;

/**
 * 이벤트 유형별 랭킹 점수 정책 — Weight × Score.
 * 조회 0.1×1, 좋아요 0.2×1, 주문 0.6×(단가×수량).
 * 좋아요 취소는 좋아요와 대칭으로 감점한다 — 감점하지 않으면 좋아요→취소 반복 토글로 점수를 부풀릴 수 있다.
 */
public final class RankingWeight {

    private static final double VIEW_WEIGHT = 0.1;
    private static final double LIKE_WEIGHT = 0.2;
    private static final double ORDER_WEIGHT = 0.6;

    private RankingWeight() {}

    public static double view() {
        return VIEW_WEIGHT;
    }

    public static double like() {
        return LIKE_WEIGHT;
    }

    public static double unlike() {
        return -LIKE_WEIGHT;
    }

    public static double order(int unitPrice, int quantity) {
        return ORDER_WEIGHT * unitPrice * quantity;
    }
}
