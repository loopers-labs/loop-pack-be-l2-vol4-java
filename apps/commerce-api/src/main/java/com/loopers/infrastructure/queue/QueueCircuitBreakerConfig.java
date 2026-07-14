package com.loopers.infrastructure.queue;

import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 입장 토큰 검증(Redis)을 감싸는 서킷브레이커.
 * Redis 장애가 지속되면 OPEN 상태로 전환해, 대기열 검증을 시도조차 하지 않고 즉시 실패(fast-fail)시킨다.
 * 이로써 죽은 Redis 커넥션 대기로 톰캣 스레드가 고갈되는 것을 막는다(fail-closed).
 */
@Configuration
public class QueueCircuitBreakerConfig {

    @Bean
    public CircuitBreaker entryTokenCircuitBreaker(CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        return circuitBreakerFactory.create("queue-token");
    }
}
