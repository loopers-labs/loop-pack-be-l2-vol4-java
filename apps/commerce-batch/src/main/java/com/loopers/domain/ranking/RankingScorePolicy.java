package com.loopers.domain.ranking;

/**
 * 기간 집계 → 랭킹 점수. 가중치는 설정(ranking.*)에서 주입되며 commerce-streamer 의 실시간 랭킹과
 * 동일한 값을 쓴다(일간/주간/월간의 점수 해석이 어긋나지 않도록 하는 크로스-앱 계약).
 *
 * <p>좋아요는 순증감(likeDelta) 합이라 음수가 될 수 있다 — 그 기간에 취소가 더 많았다는 뜻이므로
 * 점수를 끌어내리는 것이 의도된 동작이다.
 */
public class RankingScorePolicy {

    private final double viewWeight;
    private final double likeWeight;
    private final double orderWeight;

    public RankingScorePolicy(double viewWeight, double likeWeight, double orderWeight) {
        this.viewWeight = viewWeight;
        this.likeWeight = likeWeight;
        this.orderWeight = orderWeight;
    }

    public ProductRankScore score(ProductMetricsSum sum) {
        double score = viewWeight * sum.viewCount()
            + likeWeight * sum.likeDelta()
            + orderWeight * sum.salesCount();
        return new ProductRankScore(
            sum.productId(), sum.likeDelta(), sum.salesCount(), sum.viewCount(), score);
    }
}
