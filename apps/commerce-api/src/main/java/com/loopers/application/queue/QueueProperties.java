package com.loopers.application.queue;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 대기열 설정(queue.*) — 분산된 @Value 와 프로퍼티 키 리터럴 중복을 한곳에 모은다(RedisProperties 선례).
 * 잘못된 값은 기동 시점에 fail-fast 한다: batch-size 0 이면 예상 대기 계산(ceilDiv)이 ArithmeticException 으로
 * 순번 조회 전원 500 + 아무도 입장 못 하는 장애가 런타임에야 드러나기 때문.
 */
@ConfigurationProperties(prefix = "queue")
public record QueueProperties(
    @DefaultValue Admission admission,
    @DefaultValue Token token,
    @DefaultValue OrderGate orderGate
) {

    /** 입장 스케줄러 배출 속도 — 초당 배출량 = batchSize × (1000 / intervalMs). */
    public record Admission(
        @DefaultValue("100") long intervalMs,
        @DefaultValue("14") int batchSize
    ) {
        public Admission {
            requirePositive(intervalMs, "queue.admission.interval-ms");
            requirePositive(batchSize, "queue.admission.batch-size");
        }
    }

    public record Token(
        @DefaultValue("300") long ttlSeconds
    ) {
        public Token {
            requirePositive(ttlSeconds, "queue.token.ttl-seconds");
        }
    }

    /** 행사 스위치 — 게이트·입장 스케줄러·대기열 API 세 개의 수명을 함께 결정한다. */
    public record OrderGate(
        @DefaultValue("false") boolean enabled
    ) { }

    private static void requirePositive(long value, String propertyKey) {
        if (value <= 0) {
            throw new IllegalArgumentException(propertyKey + " 는 0보다 커야 합니다. (현재 값: " + value + ")");
        }
    }
}
