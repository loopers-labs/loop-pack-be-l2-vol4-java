package com.loopers.application.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "loopers.payment")
public record PaymentProperties(
    Duration lookupEmptyFailureDelay,
    Duration reconciliationDelay,
    Integer reconciliationBatchSize
) {

    private static final Duration DEFAULT_LOOKUP_EMPTY_FAILURE_DELAY = Duration.ofSeconds(10);
    private static final Duration DEFAULT_RECONCILIATION_DELAY = Duration.ofSeconds(10);
    private static final int DEFAULT_RECONCILIATION_BATCH_SIZE = 20;

    public PaymentProperties {
        if (lookupEmptyFailureDelay == null) {
            lookupEmptyFailureDelay = DEFAULT_LOOKUP_EMPTY_FAILURE_DELAY;
        }
        if (reconciliationDelay == null) {
            reconciliationDelay = DEFAULT_RECONCILIATION_DELAY;
        }
        if (reconciliationBatchSize == null) {
            reconciliationBatchSize = DEFAULT_RECONCILIATION_BATCH_SIZE;
        }
        if (lookupEmptyFailureDelay.isNegative()) {
            throw new IllegalArgumentException("lookupEmptyFailureDelay must be greater than or equal to zero");
        }
        if (reconciliationDelay.isNegative() || reconciliationDelay.isZero()) {
            throw new IllegalArgumentException("reconciliationDelay must be greater than zero");
        }
        if (reconciliationBatchSize <= 0) {
            throw new IllegalArgumentException("reconciliationBatchSize must be greater than zero");
        }
    }
}
