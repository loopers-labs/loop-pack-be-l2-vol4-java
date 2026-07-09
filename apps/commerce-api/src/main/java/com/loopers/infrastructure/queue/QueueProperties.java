package com.loopers.infrastructure.queue;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "queue")
public record QueueProperties(long schedulerIntervalMs, long throughputPerSecond) {

    public int batchSize() {
        return (int) (throughputPerSecond * schedulerIntervalMs / 1000);
    }
}
