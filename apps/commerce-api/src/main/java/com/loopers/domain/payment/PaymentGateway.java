package com.loopers.domain.payment;

/**
 * 외부 결제 시스템(PG) 호출 포트. 도메인은 구현(Feign/HTTP)을 알지 못한다.
 */
public interface PaymentGateway {

    PaymentResult requestPayment(PaymentRequest request);

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

    enum PaymentStatus {
        PENDING,   // 결과 미확정 — 폴백 포함, 이후 상태조회/콜백으로 정합성 보정
        SUCCESS,
        FAILED,
    }
}
