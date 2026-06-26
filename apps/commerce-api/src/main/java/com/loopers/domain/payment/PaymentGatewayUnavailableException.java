package com.loopers.domain.payment;

/**
 * PG 결제 요청을 처리할 수 없을 때(시스템 장애 또는 CircuitBreaker OPEN) 게이트웨이가 던지는 신호.
 * 응용 계층(Facade)이 이를 잡아 결제를 PENDING 으로 두고, 사용자에겐 정상 응답을 돌려준다.
 */
public class PaymentGatewayUnavailableException extends RuntimeException {
    public PaymentGatewayUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
