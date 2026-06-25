package com.loopers.payment.infrastructure.pg;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class PgPaymentFallbackFactory implements FallbackFactory<PgPaymentClient> {

    // PG가 실제로 응답한 비즈니스 에러(400, 404)는 그대로 전달한다.
    // CB OPEN(CallNotPermittedException)은 PG가 다운된 상태이므로 즉시 SERVICE_UNAVAILABLE로 차단한다.
    // 타임아웃·네트워크 오류·PG 500은 일시적 실패이므로 PgRetriableException으로 전달해 retry 층에서 처리한다.
    @Override
    public PgPaymentClient create(Throwable cause) {
        if (cause instanceof CoreException coreException && coreException.getErrorType() != ErrorType.INTERNAL_ERROR) {
            throw coreException;
        }
        if (cause instanceof CallNotPermittedException) {
            throw new CoreException(ErrorType.SERVICE_UNAVAILABLE, "결제 시스템이 일시적으로 불가합니다.");
        }
        throw new PgRetriableException(cause.getMessage());
    }
}
