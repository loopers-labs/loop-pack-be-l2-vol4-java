package com.loopers.infrastructure.payment;

/**
 * pg-simulator POST /api/v1/payments 요청 본문.
 * orderId 는 우리 orderNumber, amount 는 order.finalPrice 에서 도출한 값이다.
 */
public record PgPaymentRequest(
        String orderId,
        String cardType,
        String cardNo,
        Long amount,
        String callbackUrl
) {
}
