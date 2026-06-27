package com.loopers.support.external;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.lang.reflect.Method;
import java.net.SocketTimeoutException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExternalSystemGuardAspectTest {

    @DisplayName("외부 시스템 보호 어노테이션은 외부 호출 실패를 503 일시적 사용 불가능 예외로 변환한다.")
    @Test
    void convertsFailureToServiceUnavailable_whenGuardedExternalCallFails() throws Throwable {
        // arrange
        ExternalSystemGuardAspect aspect = new ExternalSystemGuardAspect(CircuitBreakerRegistry.ofDefaults(), retryRegistry(1));
        Method method = GuardedClient.class.getDeclaredMethod("requestPayment");
        ExternalSystemGuard guard = method.getAnnotation(ExternalSystemGuard.class);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getDeclaringTypeName()).thenReturn(GuardedClient.class.getName());
        when(joinPoint.proceed()).thenThrow(new RuntimeException("pg unavailable"));

        // act
        CoreException exception = catchThrowableOfType(
            () -> aspect.guard(joinPoint, guard),
            CoreException.class
        );

        // assert
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.EXTERNAL_SYSTEM_UNAVAILABLE);
        assertThat(exception.getCustomMessage()).isEqualTo("일시적으로 결제를 사용할 수 없습니다.");
    }

    @DisplayName("외부 시스템 보호 어노테이션은 timeout도 503 일시적 사용 불가능 예외로 변환한다.")
    @Test
    void convertsTimeoutToServiceUnavailable_whenGuardedExternalCallTimesOut() throws Throwable {
        // arrange
        ExternalSystemGuardAspect aspect = new ExternalSystemGuardAspect(CircuitBreakerRegistry.ofDefaults(), retryRegistry(1));
        Method method = GuardedClient.class.getDeclaredMethod("requestPayment");
        ExternalSystemGuard guard = method.getAnnotation(ExternalSystemGuard.class);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getDeclaringTypeName()).thenReturn(GuardedClient.class.getName());
        when(joinPoint.proceed()).thenThrow(new ResourceAccessException("Read timed out", new SocketTimeoutException()));

        // act
        CoreException exception = catchThrowableOfType(
            () -> aspect.guard(joinPoint, guard),
            CoreException.class
        );

        // assert
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.EXTERNAL_SYSTEM_UNAVAILABLE);
        assertThat(exception.getCustomMessage()).isEqualTo("일시적으로 결제를 사용할 수 없습니다.");
    }

    @DisplayName("ExternalSystemGuard retries a transient external failure once.")
    @Test
    void retriesTransientFailure_whenRetryIsEnabled() throws Throwable {
        // arrange
        ExternalSystemGuardAspect aspect = new ExternalSystemGuardAspect(CircuitBreakerRegistry.ofDefaults(), retryRegistry(2));
        Method method = GuardedClient.class.getDeclaredMethod("requestPayment");
        ExternalSystemGuard guard = method.getAnnotation(ExternalSystemGuard.class);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getDeclaringTypeName()).thenReturn(GuardedClient.class.getName());
        when(joinPoint.proceed())
            .thenThrow(new ResourceAccessException("connection reset"))
            .thenReturn("accepted");

        // act
        Object result = aspect.guard(joinPoint, guard);

        // assert
        assertThat(result).isEqualTo("accepted");
        verify(joinPoint, times(2)).proceed();
    }

    @DisplayName("CircuitBreaker open returns fallback without executing external call.")
    @Test
    void doesNotProceedAndReturnsServiceUnavailable_whenCircuitBreakerIsOpen() throws Throwable {
        // arrange
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(1)
            .minimumNumberOfCalls(1)
            .slidingWindowSize(1)
            .waitDurationInOpenState(Duration.ofMinutes(1))
            .build();
        ExternalSystemGuardAspect aspect = new ExternalSystemGuardAspect(CircuitBreakerRegistry.of(config), retryRegistry(1));
        Method method = GuardedClient.class.getDeclaredMethod("requestPayment");
        ExternalSystemGuard guard = method.getAnnotation(ExternalSystemGuard.class);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getDeclaringTypeName()).thenReturn(GuardedClient.class.getName());
        when(joinPoint.proceed())
            .thenThrow(new RuntimeException("first failure"))
            .thenReturn("should not be called");

        // act
        catchThrowableOfType(() -> aspect.guard(joinPoint, guard), CoreException.class);
        CoreException exception = catchThrowableOfType(
            () -> aspect.guard(joinPoint, guard),
            CoreException.class
        );

        // assert
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.EXTERNAL_SYSTEM_UNAVAILABLE);
        assertThat(exception.getCustomMessage()).isEqualTo("일시적으로 결제를 사용할 수 없습니다.");
        verify(joinPoint, times(1)).proceed();
    }

    private RetryRegistry retryRegistry(int maxAttempts) {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(maxAttempts)
            .waitDuration(Duration.ZERO)
            .build();
        return RetryRegistry.of(config);
    }

    private static class GuardedClient {
        @ExternalSystemGuard("일시적으로 결제를 사용할 수 없습니다.")
        void requestPayment() {}
    }
}
