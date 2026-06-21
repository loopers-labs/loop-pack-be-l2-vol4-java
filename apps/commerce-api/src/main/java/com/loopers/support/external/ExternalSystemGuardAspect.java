package com.loopers.support.external;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
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

    @Around("@annotation(guard)")
    public Object guard(ProceedingJoinPoint joinPoint, ExternalSystemGuard guard) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(resolveCircuitBreakerName(joinPoint, guard));
        try {
            return circuitBreaker.executeCheckedSupplier(joinPoint::proceed);
        } catch (Throwable throwable) {
            if (throwable instanceof CoreException coreException
                && coreException.getErrorType() == ErrorType.EXTERNAL_SYSTEM_UNAVAILABLE) {
                throw coreException;
            }
            throw new CoreException(ErrorType.EXTERNAL_SYSTEM_UNAVAILABLE, guard.value());
        }
    }

    private String resolveCircuitBreakerName(ProceedingJoinPoint joinPoint, ExternalSystemGuard guard) {
        if (guard.name() != null && !guard.name().isBlank()) {
            return guard.name();
        }
        if (joinPoint.getSignature() instanceof MethodSignature methodSignature) {
            return methodSignature.getDeclaringTypeName() + "." + methodSignature.getMethod().getName();
        }
        return joinPoint.getSignature().getName();
    }
}
