package com.loopers.ranking;

public final class RankingScorePolicy {

    public static final String V1 = "V1";

    private final String version;
    private final double viewWeight;
    private final double likeWeight;
    private final double orderWeight;
    private final long orderAmountUnit;

    public RankingScorePolicy(double viewWeight, double likeWeight, double orderWeight, long orderAmountUnit) {
        this(V1, viewWeight, likeWeight, orderWeight, orderAmountUnit);
    }

    private RankingScorePolicy(
        String version,
        double viewWeight,
        double likeWeight,
        double orderWeight,
        long orderAmountUnit
    ) {
        if (!V1.equals(version)) {
            throw new IllegalArgumentException("unsupported ranking score policy version: " + version);
        }
        if (!Double.isFinite(viewWeight)
            || !Double.isFinite(likeWeight)
            || !Double.isFinite(orderWeight)) {
            throw new IllegalArgumentException("ranking score policy weights must be finite");
        }
        if (orderAmountUnit <= 0) {
            throw new IllegalArgumentException("orderAmountUnit must be positive");
        }

        this.version = version;
        this.viewWeight = viewWeight;
        this.likeWeight = likeWeight;
        this.orderWeight = orderWeight;
        this.orderAmountUnit = orderAmountUnit;
    }

    public static RankingScorePolicy from(
        String version,
        double viewWeight,
        double likeWeight,
        double orderWeight,
        long orderAmountUnit
    ) {
        return new RankingScorePolicy(
            version,
            viewWeight,
            likeWeight,
            orderWeight,
            orderAmountUnit
        );
    }

    public String version() {
        return version;
    }

    public double viewWeight() {
        return viewWeight;
    }

    public double likeWeight() {
        return likeWeight;
    }

    public double orderWeight() {
        return orderWeight;
    }

    public long orderAmountUnit() {
        return orderAmountUnit;
    }

    public double viewScore() {
        return viewWeight;
    }

    public double likeScore(long likeDelta) {
        return likeDelta * likeWeight;
    }

    public double orderScore(long orderAmount) {
        return ((double) orderAmount / orderAmountUnit) * orderWeight;
    }

    public double totalScore(long viewCount, long likeDelta, long orderAmount) {
        return viewCount * viewScore()
            + likeScore(likeDelta)
            + orderScore(orderAmount);
    }
}
