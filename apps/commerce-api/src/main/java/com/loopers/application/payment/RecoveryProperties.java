package com.loopers.application.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "payment.recovery")
public record RecoveryProperties(
        Duration interval,
        Duration grace
) {
}
