package com.loopers.payment.infrastructure.pg;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class PgPaymentFallbackFactory implements FallbackFactory<PgPaymentClient> {

    // [fix] fallback이 모든 실패를 SERVICE_UNAVAILABLE로 뭉개버려, PG가 실제로 응답한 NOT_FOUND/BAD_REQUEST 같은
    // 정상적인 비즈니스 응답까지 가려지던 문제 수정 — PG가 실제로 응답한 예외는 그대로 전달하고, 진짜 장애(타임아웃, CB OPEN 등)만 대체 처리한다.
    @Override
    public PgPaymentClient create(Throwable cause) {
        if (cause instanceof CoreException coreException && coreException.getErrorType() != ErrorType.INTERNAL_ERROR) {
            throw coreException;
        }
        throw new CoreException(ErrorType.SERVICE_UNAVAILABLE, "결제 시스템이 일시적으로 불가합니다.");
    }
}
