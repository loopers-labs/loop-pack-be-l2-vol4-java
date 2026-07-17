package com.loopers.domain.ranking;

/**
 * 랭킹을 구성하는 raw 신호. Collect 구간은 이 신호별 보드에 가중치 없는 카운트만 쌓고,
 * 신호를 점수로 바꾸는 해석(가중치)은 Score 구간(합성)의 몫이다.
 *
 * <p>ORDER_COUNT/ORDER_QTY 분리: 건수(구매 결정 횟수)와 수량은 서로 유도 불가능한 정보라
 * 둘 다 보존한다 — 가중치와 달리 raw 에서 버린 정보는 합성으로 소급 복구할 수 없다.</p>
 */
public enum RankingSignal {
    VIEW("view"),
    LIKE("like"),
    ORDER_COUNT("order_count"),
    ORDER_QTY("order_qty");

    private final String segment;

    RankingSignal(String segment) {
        this.segment = segment;
    }

    public String segment() {
        return segment;
    }
}
