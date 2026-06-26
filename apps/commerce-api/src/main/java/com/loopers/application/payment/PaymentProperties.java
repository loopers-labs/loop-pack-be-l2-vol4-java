package com.loopers.application.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "loopers.payment")
public record PaymentProperties(Duration lookupEmptyFailureDelay) {

    private static final Duration DEFAULT_LOOKUP_EMPTY_FAILURE_DELAY = Duration.ofSeconds(10);

    public PaymentProperties {
        if (lookupEmptyFailureDelay == null) {
            lookupEmptyFailureDelay = DEFAULT_LOOKUP_EMPTY_FAILURE_DELAY;
        }
        if (lookupEmptyFailureDelay.isNegative()) {
            throw new IllegalArgumentException("lookupEmptyFailureDelay must be greater than or equal to zero");
        }
    }
}
