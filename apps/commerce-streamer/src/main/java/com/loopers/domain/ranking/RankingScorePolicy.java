package com.loopers.domain.ranking;

/**
 * 이벤트 → 랭킹 점수 delta 계산. 가중치는 설정(ranking.*)에서 주입된다.
 * 좋아요 취소는 음수 delta — "오늘의 순수 관심 증감"을 반영한다(전일 좋아요의 당일 취소로
 * 음수 점수가 될 수 있으나 랭킹 최하위로 밀릴 뿐이므로 허용).
 */
public class RankingScorePolicy {

    private final double viewWeight;
    private final double likeWeight;
    private final double orderWeight;

    public RankingScorePolicy(double viewWeight, double likeWeight, double orderWeight) {
        this.viewWeight = viewWeight;
        this.likeWeight = likeWeight;
        this.orderWeight = orderWeight;
    }

    /** catalog 이벤트 1건의 delta. 모르는 타입은 0 (랭킹 미반영). */
    public double catalogDelta(String type) {
        return switch (type) {
            case "ProductViewed" -> viewWeight;
            case "LikeAdded" -> likeWeight;
            case "LikeRemoved" -> -likeWeight;
            default -> 0.0;
        };
    }

    /** 주문 라인 1건의 delta — 수량 비례. (price 는 페이로드에 없어 미사용 — 설계 결정) */
    public double orderDelta(int quantity) {
        return orderWeight * quantity;
    }
}
