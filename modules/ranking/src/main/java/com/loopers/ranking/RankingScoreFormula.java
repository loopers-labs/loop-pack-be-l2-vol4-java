package com.loopers.ranking;

public final class RankingScoreFormula {
    public static final double VIEW_WEIGHT = 0.1;
    public static final double LIKE_WEIGHT = 0.2;
    public static final double SALES_WEIGHT = 0.7;

    private RankingScoreFormula() {
    }

    public static double calculate(long viewCount, long likeCount, long salesCount) {
        return viewCount * VIEW_WEIGHT
            + likeCount * LIKE_WEIGHT
            + salesCount * SALES_WEIGHT;
    }
}
