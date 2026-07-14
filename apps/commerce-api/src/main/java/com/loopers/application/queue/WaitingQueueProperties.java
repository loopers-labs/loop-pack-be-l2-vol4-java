package com.loopers.application.queue;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "loopers.waiting-queue")
public record WaitingQueueProperties(
    Boolean schedulerEnabled,
    Duration admitDelay,
    Integer admitBatchSize,
    Duration tokenTtl,
    Integer estimatedAdmitPerSecond
) {
    public int batchSize() {
        return admitBatchSize == null || admitBatchSize <= 0 ? 18 : admitBatchSize;
    }

    public Duration ttl() {
        return tokenTtl == null || tokenTtl.isNegative() || tokenTtl.isZero() ? Duration.ofMinutes(5) : tokenTtl;
    }

    public int admitPerSecond() {
        return estimatedAdmitPerSecond == null || estimatedAdmitPerSecond <= 0 ? 175 : estimatedAdmitPerSecond;
    }
}
