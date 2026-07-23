package com.loopers.batch.job.rank;

/**
 * 배치 랭킹 가중 점수 계산. 실시간 랭킹 가중치(0.1/0.2/0.7)를 계승하되, 주문은 집계 수량(sales_count) 선형으로 계산한다.
 * 실시간의 log10(금액)은 이벤트 단위 고가품 지배를 막는 장치라 집계(합계) 레벨에선 부적합(Σlog ≠ logΣ, 볼륨 감도 붕괴)해 선형을 쓴다.
 */
public final class RankScorer {

    public static final double WEIGHT_VIEW = 0.1;
    public static final double WEIGHT_LIKE = 0.2;
    public static final double WEIGHT_ORDER = 0.7;

    private RankScorer() {
    }

    public static double score(long viewSum, long likeSum, long salesSum) {
        return WEIGHT_VIEW * viewSum + WEIGHT_LIKE * likeSum + WEIGHT_ORDER * salesSum;
    }
}