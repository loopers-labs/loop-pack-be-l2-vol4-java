package com.loopers.payment.infrastructure;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * PG 호출의 회복탄력성 실행기. 호출을 {@code CircuitBreaker(Retry(RateLimiter(call)))} 순서로 감싸 실행한다.
 *
 * <p>CB OPEN / RateLimiter 거절(= PG 에 안 닿음)은 {@code CallNotPermittedException}/{@code RequestNotPermitted} 로
 * <b>그대로 전파</b>한다. 그 신호를 어떻게 처리할지(불가 응답 vs failover)는 호출자(게이트웨이/라우터)의 정책이라
 * 여기서 번역하지 않는다. 이 분리 덕에 멀티 PG failover 가 추가돼도 재사용된다.
 */
@RequiredArgsConstructor
@Component
public class PgResilienceExecutor {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;

    public <T> T call(String instance, Supplier<T> supplier) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(instance);
        Retry retry = retryRegistry.retry(instance);
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(instance);

        // 중첩 순서: 가장 안쪽 RateLimiter → Retry → CircuitBreaker(가장 바깥)
        Supplier<T> decorated =
                CircuitBreaker.decorateSupplier(circuitBreaker,
                        Retry.decorateSupplier(retry,
                                RateLimiter.decorateSupplier(rateLimiter, supplier)));
        return decorated.get();
    }
}
