package com.loopers.application.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 복구 정책. pendingThreshold = 이만큼 오래 PENDING 이면 복구 대상으로 본다(처리 최대 5초 +
 * 콜백 지연 마진을 감안), scanInterval = 스케줄러가 멈춘 결제를 조회하는 주기.
 */
@ConfigurationProperties(prefix = "payment-recovery")
public record PaymentRecoveryProperties(
    Duration pendingThreshold,
    Duration scanInterval
) {
}
