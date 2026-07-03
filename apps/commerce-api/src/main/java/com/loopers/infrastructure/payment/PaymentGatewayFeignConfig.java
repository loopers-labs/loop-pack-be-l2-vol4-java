package com.loopers.infrastructure.payment;

import feign.Retryer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * pgSimulator FeignClient 전용 설정. 타임아웃(connect/read)은 application.yml 의
 * {@code feign.client.config.pgSimulator} 로 외부화한다.
 */
@Configuration
public class PaymentGatewayFeignConfig {

    /**
     * Feign 자체 재시도를 끈다 — 재시도는 resilience4j {@code @Retry} 로 일원화하고,
     * 미도달/타임아웃 정책 분리(설계 §7.4)를 한 곳에서 통제하기 위함이다.
     */
    @Bean
    public Retryer feignRetryer() {
        return Retryer.NEVER_RETRY;
    }
}
