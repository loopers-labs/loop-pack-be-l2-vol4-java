package com.loopers.infrastructure.payment;

import feign.Logger;
import feign.Request;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

/**
 * PG Feign 설정.
 * <p>
 * Feign 자체 timeout 은 짧게 (connect 500ms / read 1.5s) — 요청 지연 100~500ms 가정 + 여유.
 * Resilience4j TimeLimiter 가 더 큰 그림(폴백/회로차단)을 책임지고, Feign 단은 연결 끊기 용도.
 */
public class PgFeignConfig {

    @Bean
    public Request.Options pgFeignOptions() {
        return new Request.Options(500, TimeUnit.MILLISECONDS, 1500, TimeUnit.MILLISECONDS, true);
    }

    @Bean
    public Logger.Level pgFeignLoggerLevel() {
        return Logger.Level.BASIC;
    }
}
