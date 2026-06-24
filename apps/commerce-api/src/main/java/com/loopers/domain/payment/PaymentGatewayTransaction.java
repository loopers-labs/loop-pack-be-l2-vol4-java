package com.loopers.domain.payment;

public record PaymentGatewayTransaction(
    PaymentStatus status,
    String reason
) {
}
