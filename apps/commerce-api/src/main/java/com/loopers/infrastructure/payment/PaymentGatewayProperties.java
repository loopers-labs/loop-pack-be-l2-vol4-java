package com.loopers.infrastructure.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(value = "payment-gateway")
public record PaymentGatewayProperties(
        String callbackUrl,
        long connectTimeoutMillis,
        long readTimeoutMillis
) {
}
