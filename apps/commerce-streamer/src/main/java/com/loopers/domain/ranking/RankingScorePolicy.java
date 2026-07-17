package com.loopers.domain.ranking;

public final class RankingScorePolicy {

    public static final double VIEW_WEIGHT = 0.1;
    public static final double LIKE_WEIGHT = 0.2;
    public static final double ORDER_WEIGHT = 0.7;

    private RankingScorePolicy() {}

    public static double viewScore() {
        return VIEW_WEIGHT * 1;
    }

    public static double likeScore() {
        return LIKE_WEIGHT * 1;
    }

    public static double orderScore(long price, long quantity) {
        double amount = (double) price * quantity;
        return ORDER_WEIGHT * Math.log10(amount + 1);
    }
}
