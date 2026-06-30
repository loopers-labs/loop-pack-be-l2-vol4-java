package com.loopers.payment.infrastructure;

import feign.FeignException;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;

class PgResilienceExecutorTest {

    private final RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(10))
            .retryExceptions(RetryableException.class)
            .build());
    private final PgResilienceExecutor executor = new PgResilienceExecutor(
            CircuitBreakerRegistry.ofDefaults(), retryRegistry, RateLimiterRegistry.ofDefaults());

    @Test
    @DisplayName("전송 실패(RetryableException)면 설정(max-attempts=3)대로 재시도해서 성공한다")
    void givenRetryableThenSuccess_whenCall_thenRetriesAndSucceeds() {
        RetryableException transportError = mock(RetryableException.class);
        AtomicInteger attempts = new AtomicInteger();
        Supplier<String> flaky = () -> {
            if (attempts.incrementAndGet() < 3) {
                throw transportError;
            }
            return "ok";
        };

        String result = executor.call("toss", flaky);

        assertAll(
                () -> assertThat(result).isEqualTo("ok"),
                () -> assertThat(attempts.get()).isEqualTo(3)
        );
    }

    @Test
    @DisplayName("응답을 받은 실패(500)는 닿은 요청이라 재시도하지 않고 즉시 전파한다")
    void givenResponseError500_whenCall_thenNoRetry() {
        FeignException.InternalServerError serverError = mock(FeignException.InternalServerError.class);
        AtomicInteger attempts = new AtomicInteger();
        Supplier<String> failing = () -> {
            attempts.incrementAndGet();
            throw serverError;
        };

        try {
            executor.call("toss", failing);
        } catch (FeignException ignored) {
            // 전파 기대
        }

        assertThat(attempts.get()).isEqualTo(1);
    }
}
