package com.loopers.payment.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("commerce.payment.recovery")
public record PaymentRecoveryProperties(
    @DefaultValue("100") int chunkSize,
    @DefaultValue("30") long retryDelaySeconds,
    @DefaultValue("15") long requestingTimeoutSeconds,
    @DefaultValue("30") long pendingTimeoutSeconds,
    @DefaultValue("120") long notFoundGraceSeconds
) {

    public PaymentRecoveryProperties {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive.");
        }
        if (retryDelaySeconds <= 0) {
            throw new IllegalArgumentException("retryDelaySeconds must be positive.");
        }
        if (requestingTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("requestingTimeoutSeconds must be positive.");
        }
        if (pendingTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("pendingTimeoutSeconds must be positive.");
        }
        if (notFoundGraceSeconds <= 0) {
            throw new IllegalArgumentException("notFoundGraceSeconds must be positive.");
        }
    }
}
