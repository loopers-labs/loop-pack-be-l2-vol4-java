package com.loopers.payment.infrastructure;

/**
 * pg-simulator 결제 요청 바디. orderId 는 우리 orderNumber 를 그대로 보낸다(PG 는 6자+ 문자열을 요구).
 */
public record PgPaymentRequest(
        String orderId,
        String cardType,
        String cardNo,
        long amount,
        String callbackUrl
) {
}
