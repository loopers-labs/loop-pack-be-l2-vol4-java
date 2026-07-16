package com.loopers.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ranking")
public record RankingProperties(
        Weight weight,
        Duration ttl
) {
    public record Weight(
            double view,   // 조회 가중치 (0.1)
            double like,   // 좋아요 가중치 (0.2)
            double order   // 주문 가중치 (0.7) score = order × log10(1 + subtotal)
    ) {
    }
}