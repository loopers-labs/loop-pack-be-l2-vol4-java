package com.loopers.application.ranking;

/**
 * 한 배치에서 productId 별로 합산한 랭킹 점수 재료.
 *
 * <p>가중치를 곱하기 전의 원시 집계값이다. 최종 점수는 {@code RankingRedisStore} 가 Redis 의
 * {@code ranking:weights} 를 읽어 {@code w_view*viewCount + w_like*likeDelta + w_order*orderScore} 로 계산한다.
 *
 * @param viewCount  조회 이벤트 수 합
 * @param likeDelta  좋아요 증감 합(+1/-1)
 * @param orderScore 주문별 log10(price*quantity+1) 합
 */
public record RankingContribution(long viewCount, long likeDelta, double orderScore) {
}
