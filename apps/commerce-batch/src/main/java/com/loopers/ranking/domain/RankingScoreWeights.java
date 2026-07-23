package com.loopers.ranking.domain;

/**
 * 랭킹 점수 가중치. 조회는 흔해 낮게, 주문은 실제 구매라 높게 둔다.
 * streamer 의 RankingScorePolicy 와 같은 값이어야 한다 — 공유 위치는 아직 미정이라
 * 지금은 배치 설정(ranking.score.*)에서 읽고, 값이 어긋나면 일간과 주간이 다른 규칙으로 돈다.
 */
public record RankingScoreWeights(double view, double like, double order) {

    public RankingScoreWeights {
        if (view < 0 || like < 0 || order < 0) {
            throw new IllegalArgumentException(
                    "랭킹 가중치는 음수일 수 없다: view=%s like=%s order=%s".formatted(view, like, order));
        }
    }
}
