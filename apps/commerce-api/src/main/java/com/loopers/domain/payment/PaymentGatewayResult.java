package com.loopers.domain.payment;

import com.loopers.domain.payment.model.PaymentStatus;

public record PaymentGatewayResult(
    String transactionKey,
    PaymentStatus status,
    String reason
) {
}
