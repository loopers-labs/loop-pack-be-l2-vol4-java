package com.loopers.domain.payment;

/**
 * PG가 '정상 작동'하며 결제 요청을 거절했을 때(요청/비즈니스 오류, 4xx).
 * 시스템 장애(PaymentGatewayUnavailableException)와 구분된다 — 이건 재시도해도 같은 결과.
 * 응용 계층은 이를 잡아 결제를 즉시 FAILED 로 확정한다.
 */
public class PaymentDeclinedException extends RuntimeException {
    public PaymentDeclinedException(String message) {
        super(message);
    }
}
