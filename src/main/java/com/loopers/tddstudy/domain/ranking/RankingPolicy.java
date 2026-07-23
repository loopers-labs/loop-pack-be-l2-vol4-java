package com.loopers.tddstudy.domain.ranking;

public class RankingPolicy {

    private static final double LIKE_WEIGHT = 0.2;
    private static final double ORDER_WEIGHT = 0.7;


    private RankingPolicy() {}

    public static double deltaOf(String eventType) {
        return switch (eventType) {
            case "PRODUCT_LIKED"   -> LIKE_WEIGHT;
            case "PRODUCT_UNLIKED" -> -LIKE_WEIGHT;
            case "ORDER_SALES"     -> ORDER_WEIGHT;
            default -> 0.0;
        };
    }
    public static double likeWeight()  { return LIKE_WEIGHT; }
    public static double salesWeight() { return ORDER_WEIGHT; }

}
