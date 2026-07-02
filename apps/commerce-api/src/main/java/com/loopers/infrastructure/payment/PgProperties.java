package com.loopers.infrastructure.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "pg-simulator")
public record PgProperties(
        String baseUrl,
        String callbackUrl,
        Duration connectTimeout,
        Duration readTimeout
) {
}