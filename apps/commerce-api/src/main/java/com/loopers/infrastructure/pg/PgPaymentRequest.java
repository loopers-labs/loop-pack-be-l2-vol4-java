package com.loopers.infrastructure.pg;

import com.loopers.domain.payment.PaymentModel;

public record PgPaymentRequest(
    Long orderId,
    Long userId,
    String cardType,
    String cardNo,
    long amount,
    String callbackUrl
) {
    public static PgPaymentRequest from(PaymentModel payment, String callbackUrl) {
        return new PgPaymentRequest(
            payment.getOrderId(),
            payment.getUserId(),
            payment.getCardType().name(),
            payment.getCardNo(),
            payment.getAmount(),
            callbackUrl
        );
    }
}
