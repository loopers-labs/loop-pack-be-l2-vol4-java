package com.loopers.payment.infrastructure;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;

/**
 * application.yml 에 바인딩된 toss CircuitBreaker 설정의 의도를 잠근다.
 * 특히 min-calls > window 면 실패율이 평가되지 않아 브레이커가 영원히 안 열리는 오설정을 회귀로 막는다.
 */
@SpringBootTest
class PgCircuitBreakerConfigTest {

    private final CircuitBreakerConfig config;

    @Autowired
    PgCircuitBreakerConfigTest(CircuitBreakerRegistry registry) {
        this.config = registry.circuitBreaker("toss").getCircuitBreakerConfig();
    }

    @Test
    @DisplayName("toss 브레이커는 불규칙 트래픽용 time-based(60s)·min-calls 10 으로 평가한다(저트래픽서도 평가 가능)")
    void givenTossConfig_thenTimeBasedWindowWithReachableMinCalls() {
        assertAll(
                () -> assertThat(config.getSlidingWindowType()).isEqualTo(SlidingWindowType.TIME_BASED),
                () -> assertThat(config.getSlidingWindowSize()).isEqualTo(60),
                () -> assertThat(config.getMinimumNumberOfCalls()).isEqualTo(10)
        );
    }

    @Test
    @DisplayName("RateLimiter 거절(RequestNotPermitted)은 PG 실패로 집계하지 않는다(ignore)")
    void givenRequestNotPermitted_thenIgnoredByCircuitBreaker() {
        assertThat(config.getIgnoreExceptionPredicate().test(mock(RequestNotPermitted.class))).isTrue();
    }
}
