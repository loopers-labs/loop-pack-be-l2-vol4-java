package com.loopers.infrastructure.queue;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "queue")
public record QueueProperties(
        long schedulerIntervalMs,
        long throughputPerSecond,
        @DefaultValue("true") boolean schedulerEnabled
) {

    public int batchSize() {
        return (int) (throughputPerSecond * schedulerIntervalMs / 1000);
    }
}
