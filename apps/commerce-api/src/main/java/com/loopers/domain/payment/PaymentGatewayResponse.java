package com.loopers.domain.payment;

public record PaymentGatewayResponse(
        String transactionKey,
        TransactionStatus status
) {
}
