package com.loopers.domain.rank;

/**
 * 주간·월간 랭킹 점수 정책 (D2 확정: 카운트 가중합).
 * 일간 랭킹 ZSET은 주문에 price×수량을 썼으나, 일간 롤업(product_metrics_daily)에는 카운트만 있으므로
 * 조회·좋아요·판매 "건수"의 가중합으로 점수를 계산한다. 가중치 계수는 일간 ZSET과 동일(0.1 / 0.2 / 0.6).
 *
 * ORDER BY용 SQL 표현식({@link #scoreSql})과 저장용 점수 계산({@link #score})이 같은 계수를 공유하도록
 * 계수를 이 한 곳에서만 정의한다 — SQL 정렬 순서와 저장 점수가 어긋나지 않게 하기 위함.
 */
public final class RankScorePolicy {

    public static final double VIEW_WEIGHT = 0.1;
    public static final double LIKE_WEIGHT = 0.2;
    public static final double SALES_WEIGHT = 0.6;

    /** MV에 적재할 상위 랭킹 개수 (주간/월간 각 TOP 100). */
    public static final int TOP_N = 100;

    private RankScorePolicy() {}

    /** 기간 합산 카운트로 가중 점수를 계산한다. */
    public static double score(long viewSum, long likeSum, long salesSum) {
        return VIEW_WEIGHT * viewSum + LIKE_WEIGHT * likeSum + SALES_WEIGHT * salesSum;
    }

    /** SUM 컬럼 표현식을 받아 ORDER BY용 가중 점수 SQL 조각을 만든다 ({@link #score}와 동일 계수). */
    public static String scoreSql(String viewSumExpr, String likeSumExpr, String salesSumExpr) {
        return VIEW_WEIGHT + " * " + viewSumExpr
            + " + " + LIKE_WEIGHT + " * " + likeSumExpr
            + " + " + SALES_WEIGHT + " * " + salesSumExpr;
    }
}
