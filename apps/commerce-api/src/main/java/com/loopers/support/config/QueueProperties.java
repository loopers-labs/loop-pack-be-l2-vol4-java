package com.loopers.support.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "queue")
public record QueueProperties(
        Admission admission,
        Token token,
        long throughputPerSecond   // 초당 처리량. 예상 대기 시간(= 순번 / 이 값) 산정 기준
) {
    public record Admission(
            long intervalMs,   // 스케줄러 실행 주기(ms). @Scheduled 은 placeholder 직접 참조
            int batchSize,     // 한 주기에 발급할 토큰 수
            boolean enabled    // 스케줄러 활성화. @ConditionalOnProperty 로 직접 참조
    ) {
    }

    public record Token(
            Duration ttl       // 입장 토큰 TTL(미사용 시 자동 만료)
    ) {
    }
}