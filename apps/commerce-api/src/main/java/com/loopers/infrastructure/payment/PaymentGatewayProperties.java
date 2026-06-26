package com.loopers.infrastructure.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pg")
public record PaymentGatewayProperties(
        String url,
        String callbackUrl,
        int connectTimeoutMs,
        int requestReadTimeoutMs,
        int queryReadTimeoutMs
) {
}
