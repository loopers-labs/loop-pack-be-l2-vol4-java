package com.loopers.application.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "loopers.outbox")
public record OutboxProperties(
    Boolean relayEnabled,
    Duration relayDelay,
    Integer relayBatchSize,
    Duration publishTimeout
) {
    public int batchSize() {
        return relayBatchSize == null || relayBatchSize <= 0 ? 50 : relayBatchSize;
    }

    public Duration timeout() {
        return publishTimeout == null || publishTimeout.isNegative() ? Duration.ofSeconds(3) : publishTimeout;
    }
}
