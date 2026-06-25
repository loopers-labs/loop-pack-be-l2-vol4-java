package com.loopers.tddstudy.infrastructure.order;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CircuitBreakerBoundaryTest {

    // 운영 설정과 동일 (TIME_BASED / 5초 / 최소 5건 / 80% / HALF_OPEN 6건)
    // wait-duration 은 파라미터로 받아 타이밍 테스트에서 짧게 사용
    private CircuitBreaker newCircuitBreaker(Duration waitDuration) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
                .slidingWindowSize(5)                       // 5초
                .minimumNumberOfCalls(5)
                .failureRateThreshold(80)                  // 80%
                .waitDurationInOpenState(waitDuration)
                .permittedNumberOfCallsInHalfOpenState(6)
                .build();
        return CircuitBreaker.of("pg-test", config);
    }

    private CircuitBreaker newCircuitBreaker() {
        return newCircuitBreaker(Duration.ofSeconds(10)); // 운영값
    }

    private void recordFailure(CircuitBreaker cb) {
        cb.onError(0, TimeUnit.MILLISECONDS, new RuntimeException("PG 실패"));
    }

    private void recordSuccess(CircuitBreaker cb) {
        cb.onSuccess(0, TimeUnit.MILLISECONDS);
    }

    @Nested
    @DisplayName("minimum-number-of-calls = 5 경계")
    class MinimumNumberOfCalls {

        @Test
        @DisplayName("4건(최소 미만)은 모두 실패해도 CLOSED 를 유지한다")
        void belowMinimum_staysClosed() {
            CircuitBreaker cb = newCircuitBreaker();

            for (int i = 0; i < 4; i++) recordFailure(cb);

            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("5건(최소 도달)이 모두 실패하면 OPEN 으로 전환된다")
        void atMinimum_transitionsToOpen() {
            CircuitBreaker cb = newCircuitBreaker();

            for (int i = 0; i < 5; i++) recordFailure(cb);

            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }
    }

    @Nested
    @DisplayName("failure-rate-threshold = 80% 경계")
    class FailureRateThreshold {

        @Test
        @DisplayName("5건 중 3건 실패(60%)는 임계값(80%) 미만이라 CLOSED 를 유지한다")
        void belowThreshold_staysClosed() {
            CircuitBreaker cb = newCircuitBreaker();

            recordFailure(cb);
            recordFailure(cb);
            recordFailure(cb);
            recordSuccess(cb);
            recordSuccess(cb);

            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("5건 중 4건 실패(80%)는 임계값과 같으므로 OPEN 으로 전환된다")
        void atThreshold_transitionsToOpen() {
            CircuitBreaker cb = newCircuitBreaker();

            recordFailure(cb);
            recordFailure(cb);
            recordFailure(cb);
            recordFailure(cb);
            recordSuccess(cb);

            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }
    }

    @Nested
    @DisplayName("wait-duration-in-open-state 경계")
    class WaitDurationInOpenState {

        @Test
        @DisplayName("OPEN 상태에서는 호출이 차단된다(CallNotPermittedException)")
        void open_rejectsCalls() {
            CircuitBreaker cb = newCircuitBreaker();
            for (int i = 0; i < 5; i++) recordFailure(cb);

            assertThatThrownBy(() -> cb.executeRunnable(() -> {}))
                    .isInstanceOf(CallNotPermittedException.class);
        }

        @Test
        @DisplayName("wait-duration 경과 후 호출하면 HALF_OPEN 으로 전환된다")
        void afterWaitDuration_transitionsToHalfOpen() throws InterruptedException {
            CircuitBreaker cb = newCircuitBreaker(Duration.ofMillis(200)); // 빠른 검증용
            for (int i = 0; i < 5; i++) recordFailure(cb);
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            Thread.sleep(250); // wait-duration(200ms) 경과

            // 경과 후 호출 시도 → 예외 없이 통과(HALF_OPEN 으로 전환되어 호출 허용)
            cb.executeRunnable(() -> {});
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        }
    }

    @Nested
    @DisplayName("permitted-number-of-calls-in-half-open-state = 6 경계")
    class HalfOpenTransition {

        @Test
        @DisplayName("HALF_OPEN 6건 중 4건 실패(66%)는 임계값 미만이라 CLOSED 로 복구된다")
        void halfOpen_belowThreshold_closes() {
            CircuitBreaker cb = newCircuitBreaker();
            cb.transitionToOpenState();
            cb.transitionToHalfOpenState();

            recordFailure(cb);
            recordFailure(cb);
            recordFailure(cb);
            recordFailure(cb);
            recordSuccess(cb);
            recordSuccess(cb);

            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("HALF_OPEN 6건 중 5건 실패(83%)는 임계값 이상이라 다시 OPEN 된다")
        void halfOpen_atThreshold_reopens() {
            CircuitBreaker cb = newCircuitBreaker();
            cb.transitionToOpenState();
            cb.transitionToHalfOpenState();

            recordFailure(cb);
            recordFailure(cb);
            recordFailure(cb);
            recordFailure(cb);
            recordFailure(cb);
            recordSuccess(cb);

            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }
    }
}
