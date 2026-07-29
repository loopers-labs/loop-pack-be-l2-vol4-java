package com.loopers.ranking.domain;

/**
 * 랭킹 점수 가중치. 조회는 흔해 낮게, 주문은 실제 구매라 높게 둔다.
 * streamer 의 RankingScorePolicy 와 같은 값이어야 한다 — 가중치는 거의 고정이라 공유 설정으로 빼지 않고
 * 양쪽 상수로 두되, 같은 조합·기대값을 두 앱 테스트에 박아 한쪽이 벗어나면 그 앱에서 깨지게 한다.
 */
public record RankingScoreWeights(double view, double like, double order) {

    public RankingScoreWeights {
        if (view < 0 || like < 0 || order < 0) {
            throw new IllegalArgumentException(
                    "랭킹 가중치는 음수일 수 없다: view=%s like=%s order=%s".formatted(view, like, order));
        }
    }

    /** streamer 의 RankingScorePolicy 와 같은 값. */
    public static RankingScoreWeights standard() {
        return new RankingScoreWeights(0.1, 0.2, 0.6);
    }
}
