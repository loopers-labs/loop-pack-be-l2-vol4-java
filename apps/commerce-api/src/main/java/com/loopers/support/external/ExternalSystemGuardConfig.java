package com.loopers.support.external;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;

@Configuration
public class ExternalSystemGuardConfig {

    private static final Duration OPEN_STATE_WAIT_DURATION = Duration.ofSeconds(10);
    private static final Duration SLOW_CALL_DURATION = Duration.ofMillis(800);
    private static final Duration RETRY_WAIT_DURATION = Duration.ofMillis(100);

    @Bean
    @ConditionalOnMissingBean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .slowCallRateThreshold(50)
            .slowCallDurationThreshold(SLOW_CALL_DURATION)
            .minimumNumberOfCalls(5)
            .slidingWindowSize(10)
            .permittedNumberOfCallsInHalfOpenState(2)
            .waitDurationInOpenState(OPEN_STATE_WAIT_DURATION)
            .build();
        return CircuitBreakerRegistry.of(config);
    }

    @Bean
    @ConditionalOnMissingBean
    public RetryRegistry retryRegistry() {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(2)
            .waitDuration(RETRY_WAIT_DURATION)
            .retryOnException(ExternalSystemGuardConfig::isRetryable)
            .build();
        return RetryRegistry.of(config);
    }

    private static boolean isRetryable(Throwable throwable) {
        if (throwable instanceof CallNotPermittedException) {
            return false;
        }
        if (throwable instanceof HttpClientErrorException clientError
            && clientError.getStatusCode().is4xxClientError()) {
            return false;
        }
        if (throwable instanceof CoreException coreException
            && coreException.getErrorType() != ErrorType.EXTERNAL_SYSTEM_UNAVAILABLE) {
            return false;
        }

        return true;
    }
}
