package com.loopers.queue.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 입장 토큰 TTL. */
@ConfigurationProperties("order-queue.entry-token")
public record OrderQueueEntryTokenProperties(long ttlSeconds) {

    public Duration ttl() {
        return Duration.ofSeconds(ttlSeconds);
    }
}
