package com.loopers.support.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 랭킹 파이프라인 설정 (Collect / Score / Serving 중 Score 구간의 파라미터).
 *
 * <p>가중치를 코드가 아닌 설정으로 두는 이유: Collect 는 raw 신호(가중치 없는 카운트)만 쌓고,
 * 가중치는 합성(ZUNIONSTORE WEIGHTS) 시점에만 적용된다. 따라서 이 값을 바꾸면 다음 합성부터
 * <b>당일 보드 전체에 소급</b> 반영된다 — 쓰기 시점에 가중치를 굽는 구조였다면 불가능한 성질.</p>
 *
 * <p>{@code orderQty} 기본 0.0: 주문 수량 신호는 raw 보드에 보존만 하고(정보 무손실) 점수 반영
 * 비율은 추후 튜닝 영역으로 이연한다. 값 자체의 튜닝(A/B)은 이 프로젝트 스코프 밖.</p>
 */
@ConfigurationProperties(prefix = "ranking")
public record RankingProperties(
        Weight weight,
        int ttlDays,
        Compose compose,
        CarryOver carryOver
) {

    public record Weight(double view, double like, double orderCount, double orderQty) {
    }

    public record Compose(boolean enabled, long intervalMs) {
    }

    public record CarryOver(boolean enabled, double weight) {
    }
}
