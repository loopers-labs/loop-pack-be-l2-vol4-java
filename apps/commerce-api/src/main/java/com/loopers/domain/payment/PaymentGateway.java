package com.loopers.domain.payment;

/**
 * 외부 결제 시스템(PG) 호출 포트. 도메인은 구현(Feign/HTTP)을 알지 못한다.
 */
public interface PaymentGateway {

    PaymentResult requestPayment(PaymentRequest request);

    /** PG에 거래의 현재 상태를 직접 조회한다. 콜백 유실 시 폴링으로 정합성을 보정하는 데 쓴다. */
    PaymentResult getTransaction(String userId, String transactionKey);

    record PaymentRequest(
        String userId,
        String orderId,
        String cardType,
        String cardNo,
        Long amount,
        String callbackUrl
    ) {}

    record PaymentResult(
        String transactionKey,
        PaymentStatus status,
        String reason
    ) {}
}
