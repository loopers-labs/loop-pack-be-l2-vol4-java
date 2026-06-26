package com.loopers.domain.payment;

/**
 * Read Timeout("요청 도달, 응답만 유실" 가능) → 자동 재시도 금지 대상.
 * 상위 타입을 상속해 CircuitBreaker 는 함께 집계하되, @Retry 에서는 ignore-exceptions 로 제외한다(설계 §7.4).
 * 타임아웃 건은 PENDING 으로 남겨 폴링/조회가 미도달을 확인한 뒤에만 처리한다(블라인드 재시도 = 이중결제 함정 회피).
 */
public class PaymentGatewayTimeoutException extends PaymentGatewayException {

    public PaymentGatewayTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
