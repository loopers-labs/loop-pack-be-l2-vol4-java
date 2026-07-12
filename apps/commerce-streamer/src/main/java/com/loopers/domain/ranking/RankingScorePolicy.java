package com.loopers.domain.ranking;

/**
 * 이벤트 타입별로 랭킹 점수에 반영할 가중치를 정의한다
 * 조회는 발생 빈도가 압도적으로 높아 가장 낮은 가중치를, 주문은 실제 구매 신호라 가장 높은 가중치를 준다.
 * 주문 점수는 price*amount 를 그대로 쓰면 초고가 상품 1건이 대중적 인기 상품을 압도하므로,
 * log10(price*amount + 1) 로 정규화한다 (+1 은 price*amount=0 일 때 log(0)=-Infinity 가 되는 것을 방지).
 */
public class RankingScorePolicy {

    private static final double VIEW_WEIGHT = 0.1;
    private static final double LIKE_WEIGHT = 0.2;
    private static final double ORDER_WEIGHT = 0.7;

    private RankingScorePolicy() {
    }

    public static double viewScore() {
        return VIEW_WEIGHT;
    }

    public static double likeScore() {
        return LIKE_WEIGHT;
    }

    public static double unlikeScore() {
        return -LIKE_WEIGHT;
    }

    public static double orderScore(double unitPrice, int quantity) {
        return ORDER_WEIGHT * Math.log10(unitPrice * quantity + 1);
    }
}
