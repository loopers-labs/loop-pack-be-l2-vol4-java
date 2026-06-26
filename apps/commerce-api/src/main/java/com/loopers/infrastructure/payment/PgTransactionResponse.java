package com.loopers.infrastructure.payment;

public record PgTransactionResponse(
    String transactionKey,
    String status,
    String reason
) {
}
