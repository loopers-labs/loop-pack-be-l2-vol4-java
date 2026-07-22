package com.loopers.ranking.domain;

/**
 * 시그널별 가중치를 곱해 랭킹 점수를 낸다. 순수 정책(I/O 없음).
 * 조회는 흔해 낮게, 주문은 실제 구매라 높게 둔다. 주문은 매출이 아니라 인기이므로 가격을 빼고 수량만 쓴다.
 */
public final class RankingScorePolicy {

    private static final double VIEW_WEIGHT = 0.1;
    private static final double LIKE_WEIGHT = 0.2;
    private static final double ORDER_WEIGHT = 0.6;

    private RankingScorePolicy() {
    }

    public static double score(RankingSignal signal, long delta) {
        return weight(signal) * delta;
    }

    private static double weight(RankingSignal signal) {
        return switch (signal) {
            case VIEW -> VIEW_WEIGHT;
            case LIKE -> LIKE_WEIGHT;
            case ORDER -> ORDER_WEIGHT;
        };
    }
}
