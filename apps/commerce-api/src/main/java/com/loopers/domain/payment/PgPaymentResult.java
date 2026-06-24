package com.loopers.domain.payment;

public record PgPaymentResult(
        String transactionKey,
        PaymentStatus status
) {
}
