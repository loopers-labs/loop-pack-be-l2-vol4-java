package com.loopers.domain.ranking;

import org.springframework.stereotype.Component;

@Component
public class RankingScorePolicy {

    private static final double VIEW_WEIGHT = 0.1;
    private static final double LIKE_WEIGHT = 0.2;
    private static final double ORDER_WEIGHT = 0.6;

    private static final double VIEW_SCORE = 1;
    private static final double LIKE_SCORE = 1;

    public double viewScore() {
        return VIEW_WEIGHT * VIEW_SCORE;
    }

    public double likeScore() {
        return LIKE_WEIGHT * LIKE_SCORE;
    }

    public double orderScore(long price, long quantity) {
        return ORDER_WEIGHT * price * quantity;
    }
}
