package com.loopers.infrastructure.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(value = "pg-simulator")
public record PgSimulatorProperties(
        String url,
        String callbackUrl
) {
}
