package com.loopers.domain.payment;

/**
 * 외부 PG 시스템에 대한 도메인 포트. 도메인은 PG를 이 추상으로만 알며,
 * 실제 HTTP 호출/타임아웃/예외 변환은 infrastructure 구현체가 담당한다.
 */
public interface PaymentGateway {

    Result requestPayment(Command command);

    record Command(
        Long userId,
        Long orderId,
        CardType cardType,
        String cardNo,
        Long amount
    ) {}

    record Result(
        String transactionKey,
        PaymentStatus status,
        String reason
    ) {}
}
