package com.loopers.domain.payment;

/**
 * 미도달(5xx·Connect Timeout 등 "돈 안 빠짐"이 증명되는 실패) → 자동 재시도 안전.
 * CircuitBreaker 는 이 타입(및 하위 타입)을 모두 실패로 집계한다.
 */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message) {
        super(message);
    }

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
