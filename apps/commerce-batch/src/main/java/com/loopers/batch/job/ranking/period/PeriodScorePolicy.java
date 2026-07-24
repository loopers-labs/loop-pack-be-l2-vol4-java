package com.loopers.batch.job.ranking.period;

/**
 * 기간 랭킹 스코어 산식(week10).
 *
 * <p><b>commerce-streamer 의 {@code RankingScorePolicy} 와 가중치가 반드시 같아야 한다.</b> 앱 경계라
 * 코드를 공유하지 않고 값을 복제한다({@code RankingKey} 를 api/streamer 양쪽에 둔 것과 같은 컨벤션).
 * 한쪽만 바꾸면 일간 랭킹과 주간/월간 랭킹이 서로 다른 스케일이 되어 비교가 불가능해진다.
 *
 * <pre>
 *   score = 0.1 × Σ조회수  +  0.2 × Σ좋아요증감  +  Σ주문스코어
 * </pre>
 *
 * <p><b>주문 가중치(0.6)가 여기 없는 이유</b>: {@code product_metrics_daily.order_score} 에 이미
 * {@code 0.6 × log10(1+건별매출)} 이 적용된 값이 들어 있다. 여기서 또 곱하면 이중 가중이 된다.
 * 건별로 미리 계산해 두는 이유는 {@code log} 가 합과 교환되지 않기 때문이다(Σ log ≠ log Σ).
 */
public final class PeriodScorePolicy {

    public static final double VIEW_WEIGHT = 0.1;
    public static final double LIKE_WEIGHT = 0.2;

    private PeriodScorePolicy() {
    }

    public static double score(long viewSum, long likeSum, double orderScoreSum) {
        return VIEW_WEIGHT * viewSum + LIKE_WEIGHT * likeSum + orderScoreSum;
    }
}
