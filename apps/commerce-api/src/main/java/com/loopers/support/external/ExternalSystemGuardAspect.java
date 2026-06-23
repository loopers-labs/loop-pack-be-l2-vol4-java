package com.loopers.support.external;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.functions.CheckedSupplier;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Aspect
@Component
public class ExternalSystemGuardAspect {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;

    @Around("@annotation(guard)")
    public Object guard(ProceedingJoinPoint joinPoint, ExternalSystemGuard guard) {
        String guardName = resolveGuardName(joinPoint, guard);
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(guardName);
        CheckedSupplier<Object> guardedCall = decorateRetryIfEnabled(joinPoint::proceed, guard, guardName);
        try {
            return circuitBreaker.executeCheckedSupplier(guardedCall);
        } catch (Throwable throwable) {
            if (throwable instanceof CoreException coreException
                && coreException.getErrorType() == ErrorType.EXTERNAL_SYSTEM_UNAVAILABLE) {
                throw coreException;
            }
            throw new CoreException(ErrorType.EXTERNAL_SYSTEM_UNAVAILABLE, guard.value());
        }
    }

    private CheckedSupplier<Object> decorateRetryIfEnabled(
        CheckedSupplier<Object> supplier,
        ExternalSystemGuard guard,
        String guardName
    ) {
        if (!guard.retryable()) {
            return supplier;
        }

        Retry retry = retryRegistry.retry(guardName);
        return Retry.decorateCheckedSupplier(retry, supplier);
    }

    private String resolveGuardName(ProceedingJoinPoint joinPoint, ExternalSystemGuard guard) {
        if (guard.name() != null && !guard.name().isBlank()) {
            return guard.name();
        }
        if (joinPoint.getSignature() instanceof MethodSignature methodSignature) {
            return methodSignature.getDeclaringTypeName() + "." + methodSignature.getMethod().getName();
        }
        return joinPoint.getSignature().getName();
    }
}
