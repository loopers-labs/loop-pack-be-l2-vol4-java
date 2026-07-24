package com.loopers.batch.domain.ranking;

/**
 * 이벤트 타입별로 랭킹 점수에 반영할 가중치를 정의한다.
 * commerce-streamer(일간 ZSET 쓰기)의 동명 클래스와 정확히 같은 값을 유지해야 한다 —
 * 앱 간 공유 모듈이 없어 각자 복제하되(RankingKeys 와 동일 컨벤션), 가중치가 어긋나면 일간과 주간/월간 점수 기준이 달라진다.
 *
 * 배치(주간/월간 집계)는 이 중 viewScore()·likeScore() 가중치를 일별 카운트 합에 곱해 점수를 만든다.
 * 주문 점수는 로그 정규화가 비선형이라 일별 집계 시점에 이미 확정(order_score)되므로, 여기서 다시 계산하지 않고 합산만 한다.
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
