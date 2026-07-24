package com.loopers.domain.ranking;

/**
 * 기간 범위로 상품별 지표를 합산한 집계 결과(Reader 산출물). JPQL GROUP BY의 constructor expression으로 채워진다.
 * <p>
 * orderAmountSum은 원천(product_metrics_hourly)의 <b>주문 금액(order_amount) 합</b>이다 — SOT에 수량이 아닌
 * 금액만 남으므로, 일간 실시간 랭킹과 같은 "금액 선형" 신호를 그대로 쓴다.
 */
public record RankAggregate(Long productId, Long likeSum, Long orderAmountSum, Long viewSum) {

    /**
     * 랭킹 점수 = 0.1·조회 + 0.2·좋아요 + 0.6·주문금액. 일간 실시간 랭킹(RankingService)과 동일한 가중치·정의다.
     */
    public double score() {
        return 0.1 * viewSum + 0.2 * likeSum + 0.6 * orderAmountSum;
    }
}
