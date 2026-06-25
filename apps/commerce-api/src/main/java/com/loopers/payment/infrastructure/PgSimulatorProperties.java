package com.loopers.payment.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("commerce.pg-simulator")
public record PgSimulatorProperties(
    @DefaultValue("http://localhost:8082") String baseUrl,
    @DefaultValue("http://localhost:8080/api/v1/payments/callback") String callbackUrl,
    @DefaultValue("1000") long connectTimeoutMillis,
    @DefaultValue("10000") long readTimeoutMillis
) {

    public PgSimulatorProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank.");
        }
        if (callbackUrl == null || callbackUrl.isBlank()) {
            throw new IllegalArgumentException("callbackUrl must not be blank.");
        }
        if (connectTimeoutMillis <= 0) {
            throw new IllegalArgumentException("connectTimeoutMillis must be positive.");
        }
        if (readTimeoutMillis <= 0) {
            throw new IllegalArgumentException("readTimeoutMillis must be positive.");
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
    }
}
