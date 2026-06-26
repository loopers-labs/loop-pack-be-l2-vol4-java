package com.loopers.application.payment;

public final class PaymentCriteria {

    private PaymentCriteria() {}

    public record Request(
        Long userId,
        Long orderId,
        String cardType,
        String cardNo
    ) {}

    /**
     * PG 콜백 페이로드 — 외부 호출이라 멱등성을 별도로 챙겨야 한다.
     * status: SUCCESS / FAILED 등 PG 명세에 정의된 값.
     */
    public record Callback(
        String transactionKey,
        String status,
        String reason
    ) {}
}
