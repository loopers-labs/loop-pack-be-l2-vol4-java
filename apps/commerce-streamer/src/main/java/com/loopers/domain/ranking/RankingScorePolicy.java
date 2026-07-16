package com.loopers.domain.ranking;

import java.math.BigDecimal;

public final class RankingScorePolicy {

    private static final double VIEW_WEIGHT = 0.1;
    private static final double LIKE_WEIGHT = 0.2;
    private static final double ORDER_WEIGHT = 0.6;

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

    public static double orderScore(BigDecimal price, long quantity) {
        return ORDER_WEIGHT * price.doubleValue() * quantity;
    }
}
