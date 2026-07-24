package com.loopers.domain.ranking;

import java.math.BigDecimal;

public record RankingWeights(double view, double like, double order) {

    public static final RankingWeights DEFAULT = new RankingWeights(0.1, 0.2, 0.6);

    public RankingWeights {
        if (view < 0 || like < 0 || order < 0) {
            throw new IllegalArgumentException("가중치는 0 이상이어야 합니다.");
        }
    }

    public double viewScore() {
        return view;
    }

    public double likeScore() {
        return like;
    }

    public double unlikeScore() {
        return -like;
    }

    public double orderScore(BigDecimal price, long quantity) {
        return order * price.doubleValue() * quantity;
    }
}
