package com.loopers.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ranking")
public record RankingProperties(
        Weight weight,
        Duration ttl,
        CarryOver carryOver   // 콜드 스타트 완화용
) {
    public record Weight(
            double view,   // 조회 가중치 (0.1)
            double like,   // 좋아요 가중치 (0.2)
            double order   // 주문 가중치 (0.7) score = order × log10(1 + subtotal)
    ) {
    }

    public record CarryOver(
            double weight,   // 전일 점수 이월 비율 (0.1)
            boolean enabled
    ) {
    }
}