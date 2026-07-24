package com.loopers.domain.ranking;

// 일별 집계(count) 기반 랭킹 점수 가중치다.
// 실시간 랭킹의 RankingWeights(주문 금액 price 기반, double)와 달리
// product_metrics_daily 에는 view/like/order '건수'만 있으므로 건수에 정수 가중치를 곱해 점수를 만든다.
// 주문 > 좋아요 > 조회 순으로 기여도가 크도록 (1, 3, 10) 을 기본값으로 둔다.
public record RankScoreWeights(long view, long like, long order) {

    public static final RankScoreWeights DEFAULT = new RankScoreWeights(1, 3, 10);

    public RankScoreWeights {
        if (view < 0 || like < 0 || order < 0) {
            throw new IllegalArgumentException("가중치는 0 이상이어야 합니다.");
        }
    }

    public long score(long viewCount, long likeCount, long orderCount) {
        return viewCount * view + likeCount * like + orderCount * order;
    }
}
